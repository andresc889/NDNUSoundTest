/*
 * Raspberry Pi GPIO code from
 * http://pi4j.com/usage.html
 */

import com.pi4j.io.gpio.*;
import net.named_data.jndn.Data;
import net.named_data.jndn.Face;
import net.named_data.jndn.MetaInfo;
import net.named_data.jndn.Name;
import net.named_data.jndn.security.KeyChain;
import net.named_data.jndn.security.SecurityException;
import net.named_data.jndn.security.identity.IdentityManager;
import net.named_data.jndn.security.identity.MemoryIdentityStorage;
import net.named_data.jndn.security.identity.MemoryPrivateKeyStorage;
import net.named_data.jndn.util.Blob;

import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    private Face m_face;
    private KeyChain m_keyChain;
    private String m_accessCode;
    private Date m_accessCodeTS;

    private Main() {
        final GpioController gpio = GpioFactory.getInstance();

        GpioPinDigitalOutput led1 = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_08, "LED #1", PinState.LOW);
        GpioPinDigitalOutput led2 = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_09, "LED #2", PinState.LOW);
        GpioPinDigitalOutput led3 = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_07, "LED #3", PinState.LOW);

        try {
            m_keyChain = buildTestKeyChain();

            m_face = new Face("localhost");
            m_face.setCommandSigningInfo(m_keyChain, m_keyChain.getDefaultCertificateName());
            m_face.registerPrefix(new Name("/thisRoom/pi"),
                    (name, intrst, face, l, i) -> {
                        System.out.println("Received interest: " + intrst.getName().toUri());

                        Pattern intrstPattern = Pattern.compile("^/thisRoom/pi/([0-9]+)/led/([0-9]+)/value/(on|off|toggle)$");
                        Matcher intrstMatcher = intrstPattern.matcher(intrst.getName().toUri());

                        if (intrstMatcher.find()) {
                            String intrstAccessCode = intrstMatcher.group(1);
                            String ledNumStr = intrstMatcher.group(2);
                            String ledTaskStr = intrstMatcher.group(3);

                            System.out.println(" |- Access Code: " + intrstAccessCode + ", Service: LED #" + ledNumStr + ", Action: " + ledTaskStr);

                            if (!intrstAccessCode.equals(m_accessCode)) {
                                return;
                            }

                            // Based on https://github.com/named-data/jndn/blob/master/examples/src/net/named_data/jndn/tests/TestPublishAsyncNfd.java
                            Data response = new Data(intrst.getName());
                            String responseStr = "Invalid LED";

                            if (ledNumStr.equals("1")) {
                                led1.toggle();
                                responseStr = "LED #1 is now " + (led1.getState().isHigh() ? "on" : "off");
                            } else if (ledNumStr.equals("2")) {
                                led2.toggle();
                                responseStr = "LED #2 is now " + (led2.getState().isHigh() ? "on" : "off");
                            } else if (ledNumStr.equals("3")) {
                                led3.toggle();
                                responseStr = "LED #3 is now " + (led3.getState().isHigh() ? "on" : "off");
                            }

                            response.setContent(new Blob(responseStr));

                            MetaInfo mi = new MetaInfo();
                            mi.setFreshnessPeriod(l);

                            response.setMetaInfo(mi);

                            try {
                                m_keyChain.sign(response, m_keyChain.getDefaultCertificateName());
                            } catch (SecurityException e) {
                                System.out.println(" |- " + e.getMessage());
                            }

                            try {
                                face.putData(response);
                                System.out.println(" |- Sent response!");
                            } catch (Exception e) {
                                System.out.println(" |- " + e.getMessage());
                            }
                        } else {
                            System.out.println(" |- Invalid name!");
                        }
                    },
                    name -> System.out.println("Failed to register prefix " + name.toUri()));

            m_accessCode = Integer.toString((int) (10.0 + Math.random() * 90.0));
            m_accessCodeTS = new Date();

            System.out.println();
            System.out.println("*** ACCESS CODE IS NOW " + m_accessCode + " ***");
            System.out.println();

            while (true) {
                m_face.processEvents();

                Date curTS = new Date();

                if (curTS.getTime() - m_accessCodeTS.getTime() >= 30000) {
                    m_accessCode = Integer.toString((int) (10.0 + Math.random() * 90.0));
                    m_accessCodeTS = curTS;

                    System.out.println();
                    System.out.println("*** ACCESS CODE IS NOW " + m_accessCode + " ***");
                    System.out.println();
                }

                Thread.sleep(500);
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    // Taken from https://github.com/named-data/jndn/blob/master/examples/src/net/named_data/jndn/tests/TestRemotePrefixRegistration.java
    public static KeyChain buildTestKeyChain() throws net.named_data.jndn.security.SecurityException {
        MemoryIdentityStorage identityStorage = new MemoryIdentityStorage();
        MemoryPrivateKeyStorage privateKeyStorage = new MemoryPrivateKeyStorage();
        IdentityManager identityManager = new IdentityManager(identityStorage, privateKeyStorage);
        KeyChain keyChain = new KeyChain(identityManager);

        try {
            keyChain.getDefaultCertificateName();
        } catch (net.named_data.jndn.security.SecurityException e) {
            keyChain.createIdentityAndCertificate(new Name("/thisRoom/identity"));
            keyChain.getIdentityManager().setDefaultIdentity(new Name("/thisRoom/identity"));
        }

        return keyChain;
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        new Main();
    }
}
