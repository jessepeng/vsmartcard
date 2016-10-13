package de.jessepeng.aidnotfound.applet;

import com.licel.jcardsim.samples.BaseApplet;

import javacard.framework.*;

public class AidNotFoundApplet extends BaseApplet {

    private final static byte CA_INS = (byte) 0xCA;

    /**
     * Only this class's install method should create the applet object.
     *
     * @param bArray  the array containing installation parameters
     * @param bOffset the starting offset in bArray
     * @param bLength the length in bytes of the parameter data in bArray
     */
    protected AidNotFoundApplet(byte[] bArray, short bOffset, byte bLength) {
        register();
    }

    /**
     * This method is called once during applet instantiation process.
     *
     * @param bArray  the array containing installation parameters
     * @param bOffset the starting offset in bArray
     * @param bLength the length in bytes of the parameter data in bArray
     * @throws ISOException if the install method failed
     */
    public static void install(byte[] bArray, short bOffset, byte bLength)
            throws ISOException {
        new AidNotFoundApplet(bArray, bOffset, bLength);
    }

    /**
     * This method is called each time the applet receives APDU.
     */
    public void process(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
//        if (buffer[ISO7816.OFFSET_INS] == CA_INS) {
//            ISOException.throwIt((short) 0x6D00);
//        } else {
            ISOException.throwIt((short) 0x6A82);
//        }
    }
}