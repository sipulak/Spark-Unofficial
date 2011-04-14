/**
 * $RCSfile: ,v $
 * $Revision: $
 * $Date: $
 * 
 * Copyright (C) 2004-2010 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.java.sipmack.media;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import javax.media.CaptureDeviceInfo;
import javax.media.CaptureDeviceManager;
import javax.media.Codec;
import javax.media.Controller;
import javax.media.ControllerClosedEvent;
import javax.media.ControllerEvent;
import javax.media.ControllerListener;
import javax.media.Format;
import javax.media.MediaLocator;
import javax.media.NoDataSourceException;
import javax.media.NoProcessorException;
import javax.media.PlugInManager;
import javax.media.Processor;
import javax.media.control.BufferControl;
import javax.media.control.PacketSizeControl;
import javax.media.control.TrackControl;
import javax.media.format.AudioFormat;
import javax.media.format.VideoFormat;
import javax.media.protocol.ContentDescriptor;
import javax.media.protocol.DataSource;
import javax.media.protocol.PushBufferDataSource;
import javax.media.protocol.PushBufferStream;
import javax.media.rtp.RTPManager;
import javax.media.rtp.ReceiveStreamListener;
import javax.media.rtp.SendStream;
import javax.media.rtp.SessionAddress;

import net.java.sipmack.sip.SIPConfig;
import net.sf.fmj.media.RegistryDefaults;
import net.sf.fmj.media.cdp.GlobalCaptureDevicePlugger;


public class VideoChannel {

    private MediaLocator locator;
    private String localIpAddress;
    private String ipAddress;
    private int localPort;
    private int portBase;
    private Format format;

    private Processor processor = null;
    private RTPManager rtpMgrs[];
    private DataSource dataOutput = null;
    private VideoReceiver videoReceiver;

    private List<SendStream> sendStreams = new ArrayList<SendStream>();

    private List<ReceiveStreamListener> receiveListeners = new ArrayList<ReceiveStreamListener>();

    private boolean started = false;

    /**
     * Creates an Audio Channel for a desired jmf locator. For instance: new MediaLocator("dsound://")
     *
     * @param locator
     * @param ipAddress
     * @param localPort
     * @param remotePort
     * @param format
     */
    public VideoChannel(MediaLocator locator,
                        String localIpAddress,
                        String ipAddress,
                        int localPort,
                        int remotePort,
                        Format format) {

        this.locator = locator;
        this.localIpAddress = localIpAddress;
        this.ipAddress = ipAddress;
        this.localPort = localPort;
        this.portBase = remotePort;
        
        this.format = new VideoFormat(VideoFormat.JPEG_RTP);
        
    }

    /**
     * Starts the transmission. Returns null if transmission started ok.
     * Otherwise it returns a string with the reason why the setup failed.
     * Starts receive also.
     */
    public synchronized String start() {
        if (started) return null;
        started = true;
        String result;

        // Create a processor for the specified jmf locator
        result = createProcessor();
        if (result != null) {
            started = false;
            return result;
        }

        // Create an RTP session to transmit the output of the
        // processor to the specified IP address and port no.
        result = createTransmitter();
        if (result != null) {
            processor.close();
            processor = null;
            started = false;
            return result;
        }

        // Start the transmission
        processor.start();

        return null;
    }

    /**
     * Add Receive Listeners. It monitors RTCP packets and signalling.
     *
     * @param listener listener to add
     */
    public void addReceiverListener(ReceiveStreamListener listener) {
        this.receiveListeners.add(listener);
        for (int i = 0; i < rtpMgrs.length; i++) {
            rtpMgrs[i].addReceiveStreamListener(listener);
        }
    }

    /**
     * Removes Receive Listener.
     *
     * @param listener listener to remove
     */
    public void removeReceiverListener(ReceiveStreamListener listener) {
        this.receiveListeners.remove(listener);
        for (int i = 0; i < rtpMgrs.length; i++) {
            rtpMgrs[i].removeReceiveStreamListener(listener);
        }
    }

    /**
     * Removes All Receive Listeners.
     */
    private void remevoAllReceiverListener() {
        for (ReceiveStreamListener listener : receiveListeners) {
            for (int i = 0; i < rtpMgrs.length; i++) {
                rtpMgrs[i].removeReceiveStreamListener(listener);
            }
        }
        this.receiveListeners.clear();
    }

    /**
     * Stops the transmission if already started.
     * Stops the receiver also.
     */
    public void stop() {
        if (!started) return;
        synchronized (this) {
            try {
                started = false;

                remevoAllReceiverListener();

                if (processor != null) {
                    processor.stop();
                    processor = null;

                    for (int i = 0; i < rtpMgrs.length; i++) {
                        rtpMgrs[i].removeReceiveStreamListener(videoReceiver);
                        rtpMgrs[i].removeSessionListener(videoReceiver);
                        rtpMgrs[i].removeTargets("Session ended.");
                        rtpMgrs[i].dispose();
                    }

                    sendStreams.clear();

                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        System.err.println("RTP Transmission Stopped.");
    }

    /**
     * Creates a JMF media PRocessor
     *
     * @return
     */
    private String createProcessor() {
        if (locator == null)
            return "Locator is null";

        
        System.out.println("CREATE PROCESSOR:" + locator);
        
        // Try to create a processor to handle the input jmf locator
        try {
            try {
				processor = javax.media.Manager.createProcessor( javax.media.Manager.createDataSource(locator));
			} catch (NoDataSourceException e) {
				e.printStackTrace();
			}
        } catch (NoProcessorException npe) {
            npe.printStackTrace();
            return "Couldn't create processor";
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return "IOException creating processor";
        }

        // Wait for it to configure
        boolean result = waitForState(processor, Processor.Configured);
        if (result == false)
            return "Couldn't configure processor";

        // Get the tracks from the processor
        TrackControl[] tracks = processor.getTrackControls();

        // Do we have atleast one track?
        if (tracks == null || tracks.length < 1)
            return "Couldn't find tracks in processor";

        // Set the output content descriptor to RAW_RTP
        // This will limit the supported formats reported from
        // Track.getSupportedFormats to only valid RTP formats.
        ContentDescriptor cd = new ContentDescriptor(ContentDescriptor.RAW_RTP);
        processor.setContentDescriptor(cd);

        Format supported[];
        Format chosen = null;
        boolean atLeastOneTrack = false;

        // Program the tracks.
        for (int i = 0; i < tracks.length; i++) {
            if (tracks[i].isEnabled()) {
            	
                supported = tracks[i].getSupportedFormats();                
               
                if (supported.length > 0) {
                    for (Format format : supported) {
                        if (format instanceof VideoFormat) {
                        	System.out.println(format);
                            if (this.format.matches(format))
                                chosen = format;
                        }
                    }
                
                    if (chosen != null) {
                        tracks[i].setFormat(chosen);
                        System.err.println("Track " + i + " is set to transmit as:");
                        System.err.println("  " + chosen);

                        if (tracks[i].getFormat() instanceof AudioFormat) {
                            int packetRate = 20;
                            PacketSizeControl pktCtrl = (PacketSizeControl) processor.getControl(PacketSizeControl.class.getName());
                            if (pktCtrl != null) {
                                try {
                                    pktCtrl.setPacketSize(getPacketSize(tracks[i].getFormat(), packetRate));
                                } catch (IllegalArgumentException e) {
                                    pktCtrl.setPacketSize(160);
                                    // Do nothing
                                }
                            }
                        }

                        atLeastOneTrack = true;
                    } else
                        tracks[i].setEnabled(false);
                } else
                    tracks[i].setEnabled(false);
            }
        }

        if (!atLeastOneTrack)
            return "Couldn't set any of the tracks to a valid RTP format";

        result = waitForState(processor, Controller.Realized);
        if (result == false)
            return "Couldn't realize processor";

        // Get the output data source of the processor
        dataOutput = processor.getDataOutput();

        return null;
    }

    /**
     * Get the best packet size for a given codec and a codec rate
     *
     * @param codecFormat
     * @param milliseconds
     * @return
     * @throws IllegalArgumentException
     */
    private int getPacketSize(Format codecFormat, int milliseconds) throws IllegalArgumentException {
        String encoding = codecFormat.getEncoding();
        if (encoding.equalsIgnoreCase(AudioFormat.G729) ||
                encoding.equalsIgnoreCase(AudioFormat.G729_RTP)) {
            return milliseconds * 1; // 1 byte per millisec
        } else if (encoding.equalsIgnoreCase(AudioFormat.ULAW) ||
                encoding.equalsIgnoreCase(AudioFormat.ULAW_RTP)) {
            return milliseconds * 8;
        } else {
            throw new IllegalArgumentException("Unknown codec type");
        }
    }

    /**
     * Use the RTPManager API to create sessions for each jmf
     * track of the processor.
     */
    private String createTransmitter() {

        // Cheated.  Should have checked the type.
        PushBufferDataSource pbds = (PushBufferDataSource) dataOutput;
        PushBufferStream pbss[] = pbds.getStreams();

        rtpMgrs = new RTPManager[pbss.length];
        SessionAddress localAddr, destAddr;
        InetAddress ipAddr;
        SendStream sendStream;

        videoReceiver = new VideoReceiver(this);

        int port;

        for (int i = 0; i < pbss.length; i++) {
            try {
                rtpMgrs[i] = RTPManager.newInstance();

                port = portBase + 2 * i;
                ipAddr = InetAddress.getByName(ipAddress);

                localAddr = new SessionAddress(InetAddress.getByName(this.localIpAddress),
                        localPort);

                destAddr = new SessionAddress(ipAddr, port);

                rtpMgrs[i].addReceiveStreamListener(videoReceiver);
                rtpMgrs[i].addSessionListener(videoReceiver);

                BufferControl bc = (BufferControl) rtpMgrs[i].getControl("javax.media.control.BufferControl");
                if (bc != null) {
                    int bl = 125;
                    bl = SIPConfig.getDefaultBufferLength() != -1 ? SIPConfig.getDefaultBufferLength()
                            : bl;

                    bc.setBufferLength(bl);
                }

                rtpMgrs[i].initialize(localAddr);
                rtpMgrs[i].addTarget(destAddr);

                System.err.println("Created RTP session at " + localPort + " to: " + ipAddress + " " + port);

                sendStream = rtpMgrs[i].createSendStream(dataOutput, i);

                sendStreams.add(sendStream);

                sendStream.start();

            } catch (Exception e) {
                e.printStackTrace();
                return e.getMessage();
            }
        }

        return null;
    }

    /**
     * Set transmit activity. If the active is true, the instance should trasmit.
     * If it is set to false, the instance should pause transmit.
     *
     * @param active
     */
    public void setTrasmit(boolean active) {
        for (SendStream sendStream : sendStreams) {
            try {
                if (active) {
                    sendStream.start();
                    System.out.println("START");
                } else {
                    sendStream.stop();
                    System.out.println("STOP");
                }
            }
            catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    /**
     * *************************************************************
     * Convenience methods to handle processor's state changes.
     * **************************************************************
     */

    private Integer stateLock = new Integer(0);
    private boolean failed = false;

    Integer getStateLock() {
        return stateLock;
    }

    void setFailed() {
        failed = true;
    }

    private synchronized boolean waitForState(Processor p, int state) {
        p.addControllerListener(new StateListener());
        failed = false;

        // Call the required method on the processor
        if (state == Processor.Configured) {
            p.configure();
        } else if (state == Processor.Realized) {
            p.realize();
        }

        // Wait until we get an event that confirms the
        // success of the method, or a failure event.
        // See StateListener inner class
        while (p.getState() < state && !failed) {
            synchronized (getStateLock()) {
                try {
                    getStateLock().wait();
                } catch (InterruptedException ie) {
                    return false;
                }
            }
        }

        if (failed)
            return false;
        else
            return true;
    }

    /**
     * *************************************************************
     * Inner Classes
     * **************************************************************
     */

    class StateListener implements ControllerListener {

        public void controllerUpdate(ControllerEvent ce) {

            // If there was an error during configure or
            // realize, the processor will be closed
            if (ce instanceof ControllerClosedEvent)
                setFailed();

            // All controller events, send a notification
            // to the waiting thread in waitForState method.
            if (ce instanceof ControllerEvent) {
                synchronized (getStateLock()) {
                    getStateLock().notifyAll();
                }
            }
        }
    }

    public static void main(String args[]) {

        InetAddress localhost;
        try {
    		// FMJ
    		RegistryDefaults.registerAll(RegistryDefaults.FMJ | RegistryDefaults.FMJ_NATIVE );
    		//PlugInManager.addPlugIn(, in, out, type)
    		try
    		{
	            String JFFMPEG_VIDEO = "net.sourceforge.jffmpeg.VideoDecoder";
	            Codec video = (Codec)Class.forName( JFFMPEG_VIDEO ).newInstance();
	
	            PlugInManager.addPlugIn( JFFMPEG_VIDEO,
	            		video.getSupportedInputFormats(),
	            		video.getSupportedInputFormats(),
	            		PlugInManager.CODEC );
    		}
    		catch (Exception e)
    		{
    			e.printStackTrace();
    		}
    		
    		// Add Device
    		GlobalCaptureDevicePlugger.addCaptureDevices();
    		
    		
    		// LOG ALL Devices
    		final Vector<CaptureDeviceInfo> vectorDevices = CaptureDeviceManager.getDeviceList(null);
    		for ( CaptureDeviceInfo infoCaptureDevice : vectorDevices )
    		{
    			System.err.println("===========> " + infoCaptureDevice.getName());
    			for (Format format : infoCaptureDevice.getFormats())
    			{
    				System.err.println(format);
    			}		
    		}
        	
        	
            localhost = InetAddress.getLocalHost();

            VideoChannel videoChannel0 = new VideoChannel(new MediaLocator("civil:/dev/video0"), localhost.getHostAddress(), localhost.getHostAddress(), 7002, 7020, new VideoFormat(VideoFormat.JPEG_RTP));
            VideoChannel videoChannel1 = new VideoChannel(new MediaLocator("civil:/dev/video1"), localhost.getHostAddress(), localhost.getHostAddress(), 7020, 7002, new VideoFormat(VideoFormat.JPEG_RTP));

            videoChannel0.start();
            videoChannel1.start();

            try {
                Thread.sleep(50000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            videoChannel0.setTrasmit(false);
            videoChannel1.setTrasmit(false);

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            videoChannel0.setTrasmit(true);
            videoChannel1.setTrasmit(true);

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            videoChannel0.stop();
            videoChannel1.stop();

        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

    }
}