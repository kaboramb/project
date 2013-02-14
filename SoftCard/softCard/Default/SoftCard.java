// TODO : créer classe d'exception personelle

package Default;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.List;

import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import javax.smartcardio.TerminalFactory;

/**
 * This class allow user to send requests to the smartcard :
 * cipher, uncipher, get the public key, sign data or verify it
 * @author Emmanuel Mocquet
 * @version 1.0
 */
public class SoftCard {
	public static final byte CLA_SMARTCARD = (byte) 0xB0;

	// Constants identifying each applet on the smartcard
	public static byte[] GEN_RANDOM_AID = { (byte)0x01, (byte)0x02, (byte)0x03, (byte)0x04, (byte)0x05, (byte)0x06, (byte)0x07, (byte)0x08, (byte)0x09, (byte)0x00, (byte)0x01 };
	public static byte[] SIGN_AID = { (byte)0x01, (byte)0x02, (byte)0x03, (byte)0x04, (byte)0x05, (byte)0x06, (byte)0x07, (byte)0x08, (byte)0x09, (byte)0x00, (byte)0x02 };
	public static byte[] STORE_ID_AID = { (byte)0x01, (byte)0x02, (byte)0x03, (byte)0x04, (byte)0x05, (byte)0x06, (byte)0x07, (byte)0x08, (byte)0x09, (byte)0x00, (byte)0x03 };
	public static byte[] CIPHER_AID = { (byte)0x01, (byte)0x02, (byte)0x03, (byte)0x04, (byte)0x05, (byte)0x06, (byte)0x07, (byte)0x08, (byte)0x09, (byte)0x00, (byte)0x04 };
	public static byte[] PIN_AID = { (byte)0x01, (byte)0x02, (byte)0x03, (byte)0x04, (byte)0x05, (byte)0x06, (byte)0x07, (byte)0x08, (byte)0x09, (byte)0x00, (byte)0x08 };
	public static byte[] TUNNEL_AID = { (byte)0x01, (byte)0x02, (byte)0x03, (byte)0x04, (byte)0x05, (byte)0x06, (byte)0x07, (byte)0x08, (byte)0x09, (byte)0x00, (byte)0x09 };


	// Constants associated with the generation of random number's applet
	public static final byte INS_GEN = 0x00;
	
	// Default size of the generated password
	private static final int SIZE_PWD = 0x40;
	
	// Constants for verification and signature of data with the associated applet
	public static final byte INS_SIGN = 0x04;
	public static final byte INS_VERIF = 0x05;

	// Constants associated with the ciphering applet
	public static final byte INS_CIPHER = 0x00;
	public static final byte INS_UNCIPHER = 0x01;
	public static final byte INS_GET_EXPONENT = 0x02;
	public static final byte INS_GET_MODULUS = 0x03;

	// Constants associated with the applet handling the PIN code
	public static final byte INS_VERIF_PIN = 0x00;                              
	public static final byte INS_REMAINING_TRIES = 0x01;                        
	public static final byte INS_RESET_PIN = 0x02;
	public static final byte INS_UNLOCK_WITH_PUK = 0x03;
	public static final byte INS_GET_PIN = 0x04;
	public static final byte INS_GET_PUK = 0x05;

	// Constants associated with the applet storing user's credentials
	public static final byte INS_STORE_LOGIN = 0x00;
	public static final byte INS_STORE_MDP = 0x01;
	private static final byte INS_GET_CRED = 0x02;


	private TerminalFactory factory;
	private static List<CardTerminal> terminals;
	private static SoftCard instance = null;
	private static Card card = null;
	private static CardChannel channel = null;
	private boolean unlocked = false;


//	private static String bytesToHexString(byte[] bytes) {
//		StringBuffer sb = new StringBuffer();
//		for (byte b : bytes) {
//			sb.append(String.format("0x%02x ", b));
//		}
//		return new String(sb);
//	}

	private SoftCard() throws CardException {
		factory = TerminalFactory.getDefault();
		terminals = factory.terminals().list();

	}

	public static SoftCard getInstance() throws CardException{
		if (instance == null) {
			instance = new SoftCard();
			CardTerminal terminal = terminals.get(0);

			/* Connexion à la carte */
			card = terminal.connect("T=1");
			channel = card.getBasicChannel();
		}

		return instance;
	}


	public byte[] getPublicKey() throws Exception {
		/* Sélection de l'applet */
		ResponseAPDU r = channel.transmit(new CommandAPDU(0x00, (byte)0xA4, 0x04, 0x00, CIPHER_AID));
		if (r.getSW() != 0x9000) {
			throw new Exception("Could not select the applet.");
		}

		// Récupération de l'exposant
		r = channel.transmit(new CommandAPDU((byte) CLA_SMARTCARD, INS_GET_EXPONENT, 0x00, 0x00, 1));
		if (r.getSW() != 0x9000) {
			//throw new Exception("Could not retrieve the exponent.");
			System.out.println("Err code : " + r.getSW());
			throw new Exception("Err code : " + r.getSW());
		}

		BigInteger exp = new BigInteger(1, r.getData());
		// Récupération du modulus
		r = channel.transmit(new CommandAPDU((byte) CLA_SMARTCARD, INS_GET_MODULUS, 0x00, 0x00, 1));
		if (r.getSW() != 0x9000) {
			throw new Exception("Could not retrieve the modulus.");
		}
		BigInteger mod = new BigInteger(1, r.getData());
		KeyFactory kf = KeyFactory.getInstance("RSA");
		RSAPublicKeySpec pubKeySpec = new RSAPublicKeySpec(mod, exp);
		PublicKey publicKey = kf.generatePublic(pubKeySpec);
		return publicKey.getEncoded();
	}

	public void disconnect() throws CardException{
		card.disconnect(false);
		instance = null;
		unlocked = false;
	}

	public byte[] getRandomNumber(byte nb) throws CardException, Exception{
		// Selecting the applet
		ResponseAPDU r = channel.transmit(new CommandAPDU((byte)0x00, (byte)0xA4, (byte)0x04, (byte)0x00, GEN_RANDOM_AID));
		if (r.getSW() != 0x9000) {
			throw new Exception("Could not select the applet. : " + r.getSW());
		}

		// Generating number 
		r = channel.transmit(new CommandAPDU((byte) CLA_SMARTCARD, INS_GEN, nb, 0x00));
		if (r.getSW() != 0x9000) {
			throw new Exception("Could not retrieve the random number.");
		}

		return r.getData();

	}

	public byte[] decryptData(byte[] data) throws Exception {
		// Selecting the applet
		ResponseAPDU r = channel.transmit(new CommandAPDU(0x00, (byte)0xA4, 0x04, 0x00, CIPHER_AID));
		if (r.getSW() != 0x9000) {
			throw new Exception("Could not select the applet.");
		}
		// Decrypt data with the applet
		r = channel.transmit(new CommandAPDU((byte) CLA_SMARTCARD, INS_UNCIPHER, 0x00, 0x00, data));
		if (r.getSW() == 0x4247) {
			throw new Exception("APDU Exception");
		}
		else if (r.getSW() == 0x0001) {
			throw new Exception("Unitialized key");
		}
		else if (r.getSW() == 0x0002) {
			throw new Exception("Invalid key");
		}
		else if (r.getSW() == 0x0003) {
			throw new Exception("Illegal use");
		}
		else if (r.getSW() == 0x0004) {
			throw new Exception("Illegal value");
		}
		else if (r.getSW() == 0x0005) {
			throw new Exception("No such algorithm");
		}
		else if (r.getSW() == 0x4249) {
			throw new Exception("Security exception");
		}
		return r.getData();
	}

	public byte[] encryptData(byte[] data) throws Exception {
		// Selecting the applet
		ResponseAPDU r = channel.transmit(new CommandAPDU(0x00, (byte)0xA4, (byte)0x04, 0x00, CIPHER_AID));
		if (r.getSW() != 0x9000) {
			throw new Exception("Could not select the applet.");
		}

		// Encrypt data with the applet
		r = channel.transmit(new CommandAPDU((byte) CLA_SMARTCARD, INS_CIPHER, 0x00, 0x00, data));
		if (r.getSW() == 0x4247) {
			throw new Exception("APDU Exception");
		}
		else if (r.getSW() == 0x0001) {
			throw new Exception("Unitialized key");
		}
		else if (r.getSW() == 0x0002) {
			throw new Exception("Invalid key");
		}
		else if (r.getSW() == 0x0003) {
			throw new Exception("Illegal use");
		}
		else if (r.getSW() == 0x0004) {
			throw new Exception("Illegal value");
		}
		else if (r.getSW() == 0x0005) {
			throw new Exception("No such algorithm");
		}
		else if (r.getSW() == 0x4249) {
			throw new Exception("Security exception");
		}
		return r.getData();
	}

	/*
	 * TODO : Appeler une méthode la carte !
	 */
	public boolean isUnlocked(){
		return this.unlocked;
	}

	public boolean unlock(byte[] pin) throws Exception {
		// Selecting the applet
		ResponseAPDU r = channel.transmit(new CommandAPDU(0x00, (byte)0xA4, 0x04, 0x00, PIN_AID));
		if (r.getSW() != 0x9000) {
			throw new Exception("Could not select the applet.");
		}
		// Checking if the card is PIN-locked
		r = channel.transmit(new CommandAPDU((byte) CLA_SMARTCARD, INS_REMAINING_TRIES, 0x00, 0x00));
		if (r.getSW() != 0x9000) {
			throw new Exception("Could not check if the card was PIN-locked.");
		}

		if (r.getData()[0] < 0) {
			return false;
		}
		else {
			// Verify PIN
			r = channel.transmit(new CommandAPDU((byte) CLA_SMARTCARD, INS_VERIF_PIN, 0x00, 0x00, pin));
			if (r.getSW() != 0x9000) {
				throw new Exception("Could not verify the PIN.");
			}

			return (r.getData()[0] == 1) ? true : false;
		}
	}


	public boolean storeCredentials(byte[] data) throws Exception {
		// Selecting the applet
		ResponseAPDU r = channel.transmit(new CommandAPDU(0x00, (byte)0xA4, 0x04, 0x00, STORE_ID_AID));
		if (r.getSW() != 0x9000) {
			throw new Exception("Could not select the applet.");
		}
		
		String strData = new String(data);
		int i = 0;
		boolean found = false; 
		
		// Recherche du motif " "(espace) servant de délimiteur
		while (i < strData.length() && !found) {
			if (strData.charAt(i) == ' ') {
				found = true;
			}
			else {
				i++;
			}
		}
		
		if (found) {
			byte[] login = new byte[i];
			byte[] mdp = new byte[data.length - i - 1];
			System.arraycopy(login, 0, data, 0, i);
			System.arraycopy(mdp, 0, data, i, data.length - i - 1);

			// Store login
			r = channel.transmit(new CommandAPDU((byte) CLA_SMARTCARD, INS_STORE_LOGIN, 0x00, 0x00, login));
			if (r.getSW() != 0x9000) {
				throw new Exception("Could not store data.");
			}
			
			// Store mdp
			r = channel.transmit(new CommandAPDU((byte) CLA_SMARTCARD, INS_STORE_MDP, 0x00, 0x00, mdp));
			if (r.getSW() != 0x9000) {
				throw new Exception("Could not store data.");
			}
			return (r.getData()[0] == 1) ? true : false;
		}
		else {
			throw new Exception("Could not store data");
		}
	}

	public byte[] retrieveCredentials() throws Exception {
		// Selecting the applet
		ResponseAPDU r = channel.transmit(new CommandAPDU(0x00, (byte)0xA4, 0x04, 0x00, STORE_ID_AID));
		if (r.getSW() != 0x9000) {
			throw new Exception("Could not select the applet.");
		}

		// Retrieve data
		r = channel.transmit(new CommandAPDU((byte) CLA_SMARTCARD, INS_GET_CRED, 0x00, 0x00));
		if (r.getSW() != 0x9000) {
			throw new Exception("Could not retrieve data.");
		}
		return r.getData();
	}

	/**
	 * This method allow the administrator to get the PIN unlocking the card.
	 * It will user another method on the card that will be callable only
	 * once.
	 * @throws Exception - if the method was called more than once, or if another
	 * error occured on the smarcard's side.   
	 * @return the code PIN, as a bytes' array
	 */
	public byte[] getPIN() throws Exception{
		// Selecting the applet
		ResponseAPDU r = channel.transmit(new CommandAPDU(0x00, (byte)0xA4, 0x04, 0x00, PIN_AID));
		if (r.getSW() != 0x9000) {
			throw new Exception("Could not select the applet.");
		}

		// Retrieve PIN
		r = channel.transmit(new CommandAPDU((byte) CLA_SMARTCARD, INS_GET_PIN, 0x00, 0x00));
		if (r.getSW() != 0x9000) {
			throw new Exception("Could not retrieve code PIN.");
		}
		System.out.println(r.getSW());
		return r.getData();
	}

	/**
	 * This method allow the administrator to get the PUN unlocking the PIN.
	 * It will use another method on the card that will be callable only
	 * once.
	 * @throws Exception - if the method was called more than once, or if another
	 * error occured on the smarcard's side.
	 * @return the code PUK, as a bytes' array
	 */
	public byte[] getPUK() throws Exception{
		// Selecting the applet
		ResponseAPDU r = channel.transmit(new CommandAPDU(0x00, (byte)0xA4, 0x04, 0x00, PIN_AID));
		if (r.getSW() != 0x9000) {
			throw new Exception("Could not select the applet.");
		}

		// Retrieve PUK
		r = channel.transmit(new CommandAPDU((byte) CLA_SMARTCARD, INS_GET_PUK, 0x00, 0x00));
		if (r.getSW() != 0x9000) {
			throw new Exception("Could not retrieve PUK.");
		}
		return r.getData();
	}

	/**
	 * This method allow the user to get a new password from the card.
	 * @throws Exception - if an error occured on the smarcard's side.
	 * @return the new password, as a bytes' array
	 */
	public byte[] resetPassword() throws Exception{ 
		if (isUnlocked()) {
			// Selecting the applet
			ResponseAPDU r = channel.transmit(new CommandAPDU(0x00, (byte)0xA4, 0x04, 0x00, GEN_RANDOM_AID));
			if (r.getSW() != 0x9000) {
				throw new Exception("Could not select the applet.");
			}

			// Generate a new password
			r = channel.transmit(new CommandAPDU((byte) CLA_SMARTCARD, INS_GEN, SIZE_PWD, 0x00));
			if (r.getSW() != 0x9000) {
				throw new Exception("Could not change the password");
			}
			return r.getData();
		}
		else {
			throw new Exception("Card locked");
		}
	}
}