/*
 * Copyright (c) 2015 Samsung Electronics Co., Ltd. All rights reserved. 
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that 
 * the following conditions are met:
 * 
 *     * Redistributions of source code must retain the above copyright notice, 
 *       this list of conditions and the following disclaimer. 
 *     * Redistributions in binary form must reproduce the above copyright notice, 
 *       this list of conditions and the following disclaimer in the documentation and/or 
 *       other materials provided with the distribution. 
 *     * Neither the name of Samsung Electronics Co., Ltd. nor the names of its contributors may be used to endorse
 *       or promote products derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.vsmartcard.acardemulator;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.samsung.android.sdk.SsdkUnsupportedException;
import com.samsung.android.sdk.accessory.SA;
import com.samsung.android.sdk.accessory.SAAgent;
import com.samsung.android.sdk.accessory.SAAuthenticationToken;
import com.samsung.android.sdk.accessory.SAPeerAgent;
import com.samsung.android.sdk.accessory.SASocket;
import com.vsmartcard.acardemulator.emulators.EmulatorSingleton;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;

import javax.security.cert.CertificateException;
import javax.security.cert.X509Certificate;

public class SmartcardProviderService extends SAAgent {
    private static final String TAG = "ACardEmulator";
    private static final int ACCESSORY_CHANNEL_ID = 104;
    private static final Class<ServiceConnection> SASOCKET_CLASS = ServiceConnection.class;
    private final IBinder mBinder = new LocalBinder();
    private ServiceConnection mConnectionHandler = null;
    Handler mHandler = new Handler();

    public SmartcardProviderService() {
        super(TAG, SASOCKET_CLASS);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        SA mAccessory = new SA();
        try {
            mAccessory.initialize(this);
            EmulatorSingleton.createEmulator(this);
        } catch (SsdkUnsupportedException e) {
            // try to handle SsdkUnsupportedException
            if (processUnsupportedException(e)) {
                EmulatorSingleton.createEmulator(this);
                return;
            }
        } catch (Exception e1) {
            e1.printStackTrace();
            /*
             * Your application can not use Samsung Accessory SDK. Your application should work smoothly
             * without using this SDK, or you may want to notify user and close your application gracefully
             * (release resources, stop Service threads, close UI thread, etc.)
             */
            stopSelf();
        }
        findPeerAgents();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    protected void onFindPeerAgentsResponse(SAPeerAgent[] peerAgents, int result) {
        switch (result) {
            case PEER_AGENT_FOUND:
                break;
            case FINDPEER_SERVICE_NOT_FOUND:
                break;
            case FINDPEER_DEVICE_NOT_CONNECTED:
                break;
            default:
                break;
        }
        Log.d(TAG, "onFindPeerAgentResponse : result =" + result);
    }

    @Override
    protected void onServiceConnectionRequested(SAPeerAgent peerAgent) {
        if (peerAgent != null) {
            authenticatePeerAgent(peerAgent);
        }
    }

    @Override
    protected void onServiceConnectionResponse(SAPeerAgent peerAgent, SASocket socket, int result) {
        if (result == SAAgent.CONNECTION_SUCCESS) {
            if (socket != null) {
                mConnectionHandler = (ServiceConnection) socket;
                Toast.makeText(getBaseContext(), "connection established", Toast.LENGTH_SHORT).show();
            }
        } else if (result == SAAgent.CONNECTION_ALREADY_EXIST) {
            Log.e(TAG, "onServiceConnectionResponse, CONNECTION_ALREADY_EXIST");
        }
    }

    private static byte[] getApplicationCertificate(Context context) {
        byte[] cert = new byte[0];
        String packageName = context.getPackageName();
        try {
            PackageInfo pkgInfo = context.getPackageManager().getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
            if (pkgInfo != null) {
                Signature[] sigs = pkgInfo.signatures;
                if (sigs != null) {
                    ByteArrayInputStream stream = new ByteArrayInputStream(sigs[0].toByteArray());
                    X509Certificate x509cert = X509Certificate.getInstance(stream);
                    cert = x509cert.getPublicKey().getEncoded();
                }
            }
        } catch (PackageManager.NameNotFoundException | CertificateException e) {
            Log.e(TAG, e.getMessage());
        }
        return cert;
    }

    @Override
    protected void onAuthenticationResponse(SAPeerAgent peerAgent, SAAuthenticationToken authToken, int error) {
        if (authToken.getAuthenticationType() == SAAuthenticationToken.AUTHENTICATION_TYPE_CERTIFICATE_X509) {
            byte[] myAppKey = getApplicationCertificate(getApplicationContext());
            if (authToken.getKey().length == myAppKey.length) {
                if (Arrays.equals(authToken.getKey(), myAppKey)) {
                    acceptServiceConnectionRequest(peerAgent);
                }
            }
        }
        rejectServiceConnectionRequest(peerAgent);
    }

    @Override
    protected void onError(SAPeerAgent peerAgent, String errorMessage, int errorCode) {
        super.onError(peerAgent, errorMessage, errorCode);
    }

    private boolean processUnsupportedException(SsdkUnsupportedException e) {
        e.printStackTrace();
        int errType = e.getType();
        if (errType == SsdkUnsupportedException.VENDOR_NOT_SUPPORTED
                || errType == SsdkUnsupportedException.DEVICE_NOT_SUPPORTED) {
            /*
             * Your application can not use Samsung Accessory SDK. You application should work smoothly
             * without using this SDK, or you may want to notify user and close your app gracefully (release
             * resources, stop Service threads, close UI thread, etc.)
             */
            stopSelf();
        } else if (errType == SsdkUnsupportedException.LIBRARY_NOT_INSTALLED) {
            Log.e(TAG, "You need to install Samsung Accessory SDK to use this application.");
        } else if (errType == SsdkUnsupportedException.LIBRARY_UPDATE_IS_REQUIRED) {
            Log.e(TAG, "You need to update Samsung Accessory SDK to use this application.");
        } else if (errType == SsdkUnsupportedException.LIBRARY_UPDATE_IS_RECOMMENDED) {
            Log.e(TAG, "We recommend that you update your Samsung Accessory SDK before using this application.");
            return false;
        }
        return true;
    }

    public class LocalBinder extends Binder {
        public SmartcardProviderService getService() {
            return SmartcardProviderService.this;
        }
    }

    public class ServiceConnection extends SASocket {
        public ServiceConnection() {
            super(ServiceConnection.class.getName());
        }

        @Override
        public void onError(int channelId, String errorMessage, int errorCode) {
        }

        @Override
        public void onReceive(int channelId, byte[] data) {
            if (mConnectionHandler == null) {
                return;
            }
            final byte[] sendResponse;
            if (data[0] == 'd') {
                // APDU is sent
                byte[] newData = new byte[data.length - 1];
                System.arraycopy(data, 1, newData, 0, data.length - 1);
                byte[] response = EmulatorSingleton.process(getApplicationContext(), newData);
                sendResponse = new byte[response.length + 1];
                sendResponse[0] = 'd';
                System.arraycopy(response, 0, sendResponse, 1, response.length);
            } else if (data[0] == 'a') {
                // request for aids
                String[] aidList = EmulatorSingleton.getRegisteredAids(getApplicationContext());
                String aidListJoined = TextUtils.join(",", aidList);
                sendResponse = new byte[aidListJoined.length() + 1];
                sendResponse[0] = 'a';
                byte[] aids = aidListJoined.getBytes();
                System.arraycopy(aids, 0, sendResponse, 1, aids.length);
            } else {
                Log.e(TAG, "unknown message from consumer: " + new String(data));
                return;
            }
            new Thread(new Runnable() {
                public void run() {
                try {
                    mConnectionHandler.secureSend(ACCESSORY_CHANNEL_ID, sendResponse);
                    //mConnectionHandler.send(ACCESSORY_CHANNEL_ID, sendResponse);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                }
            }).start();
        }

        @Override
        protected void onServiceConnectionLost(int reason) {
            mConnectionHandler = null;
            EmulatorSingleton.deactivate();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getBaseContext(), "connection lost", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}
