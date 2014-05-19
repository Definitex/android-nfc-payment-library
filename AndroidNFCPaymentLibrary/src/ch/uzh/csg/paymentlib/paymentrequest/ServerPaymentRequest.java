package ch.uzh.csg.paymentlib.paymentrequest;

import ch.uzh.csg.nfclib.util.Utils;
import ch.uzh.csg.paymentlib.exceptions.IllegalArgumentException;

//TODO: javadoc
public class ServerPaymentRequest {
	private static final int NOF_BYTES_FOR_PAYLOAD_LENGTH = 2; // 2 bytes for the payload length, up to 65536 bytes
	
	private int version;
	private byte nofSignatures;
	
	private PaymentRequest paymentRequestPayer;
	private PaymentRequest paymentRequestPayee;
	
	private ServerPaymentRequest() {
	}
	
	public ServerPaymentRequest(PaymentRequest paymentRequestPayer) throws IllegalArgumentException {
		this.version = 1;
		this.nofSignatures = 1;
		
		checkParameters(version, nofSignatures, paymentRequestPayer);
		
		this.paymentRequestPayer = paymentRequestPayer;
	}

	public ServerPaymentRequest(PaymentRequest paymentRequestPayer, PaymentRequest paymentRequestPayee) throws IllegalArgumentException {
		this.version = 1;
		this.nofSignatures = 2;
		
		checkParameters(version, nofSignatures, paymentRequestPayer, paymentRequestPayee);
		
		this.paymentRequestPayer = paymentRequestPayer;
		this.paymentRequestPayee = paymentRequestPayee;
	}

	private static void checkParameters(int version, byte nofSignatures, PaymentRequest paymentRequestPayer) throws IllegalArgumentException {
		if (version <= 0 || version > 255)
			throw new IllegalArgumentException("The version number must be between 1 and 255.");
		
		if (nofSignatures <= 0 || nofSignatures > 2)
			throw new IllegalArgumentException("The Server Payment Request can only handle 1 or 2 signatures.");
		
		checkPaymentRequest(paymentRequestPayer, "payer");
	}
	
	private static void checkParameters(int version, byte nofSignatures, PaymentRequest paymentRequestPayer, PaymentRequest paymentRequestPayee) throws IllegalArgumentException {
		checkParameters(version, nofSignatures, paymentRequestPayer);
		checkPaymentRequest(paymentRequestPayee, "payee");
		
		if (!paymentRequestPayee.equals(paymentRequestPayer))
			throw new IllegalArgumentException("The tow payment requests must be equals.");
	}
	
	private static void checkPaymentRequest(PaymentRequest paymentRequest, String role) throws IllegalArgumentException {
		if (paymentRequest == null)
			throw new IllegalArgumentException("The "+role+"'s Payment Request can't be null.");
		
		int maxPayloadLength = (int) Math.pow(2, NOF_BYTES_FOR_PAYLOAD_LENGTH*Byte.SIZE) - 1;
		byte[] payload = paymentRequest.getPayload();
		if (payload == null || payload.length == 0 || payload.length > maxPayloadLength)
			throw new IllegalArgumentException("The "+role+"'s payload can't be null, empty or longer than "+maxPayloadLength+" bytes.");
		
		byte[] signature = paymentRequest.getSignature();
		if (signature == null || signature.length == 0)
			throw new IllegalArgumentException("The "+role+"'s Payment Request is not signed.");
		
		if (signature.length > 255)
			throw new IllegalArgumentException("The "+role+"'s signature is too long. A signature algorithm with output longer than 255 bytes is not supported.");
	}
	
	public PaymentRequest getPaymentRequestPayer() {
		return paymentRequestPayer;
	}
	
	public PaymentRequest getPaymentRequestPayee() {
		return paymentRequestPayee;
	}
	
	public byte[] encode() {
		int outputLength;
		if (nofSignatures == 1) {
			/*
			 * version
			 * + nofSignatures
			 * + paymentRequestPayer.getPayload().length
			 * + paymentRequestPayer.getPayload()
			 * + paymentRequestPayer.getSignature().length
			 * + paymentRequestPayer.getSignature()
			 */
			outputLength = 1+1+NOF_BYTES_FOR_PAYLOAD_LENGTH+paymentRequestPayer.getPayload().length+1+paymentRequestPayer.getSignature().length;
		} else {
			/*
			 * version
			 * + nofSignatures
			 * + paymentRequestPayer.getPayload().length
			 * + paymentRequestPayer.getPayload()
			 * + paymentRequestPayer.getSignature().length
			 * + paymentRequestPayer.getSignature()
			 * + keyNumberPayee
			 * + paymentRequestPayee.getSignature()
			 */
			outputLength = 1+1+NOF_BYTES_FOR_PAYLOAD_LENGTH+paymentRequestPayer.getPayload().length+1+paymentRequestPayer.getSignature().length+1+paymentRequestPayee.getSignature().length;
		}
		
		int index = 0;
		byte[] result = new byte[outputLength];
		
		result[index++] = (byte) version;
		result[index++] = nofSignatures;
		
		byte[] paloadLengthBytes = Utils.getShortAsBytes((short) paymentRequestPayer.getPayload().length);
		for (byte b : paloadLengthBytes) {
			result[index++] = b;
		}
		for (byte b : paymentRequestPayer.getPayload()) {
			result[index++] = b;
		}
		result[index++] = (byte) paymentRequestPayer.getSignature().length;
		for (byte b : paymentRequestPayer.getSignature()) {
			result[index++] = b;
		}
		if (nofSignatures > 1) {
			result[index++] = (byte) paymentRequestPayee.getKeyNumber();
			for (byte b : paymentRequestPayee.getSignature()) {
				result[index++] = b;
			}
		}
		return result;
	}
	
	public static ServerPaymentRequest decode(byte[] bytes) throws IllegalArgumentException {
		if (bytes == null)
			throw new IllegalArgumentException("The argument can't be null.");
		
		try {
			int index = 0;
			
			ServerPaymentRequest spr = new ServerPaymentRequest();
			
			spr.version = (bytes[index++] & 0xFF);
			spr.nofSignatures = bytes[index++];
			
			byte[] indicatedPayloadLength = new byte[NOF_BYTES_FOR_PAYLOAD_LENGTH];
			for (int i=0; i<NOF_BYTES_FOR_PAYLOAD_LENGTH; i++) {
				indicatedPayloadLength[i] = bytes[index++];
			}
			int paymentRequestPayerPayloadLength = (Utils.getBytesAsShort(indicatedPayloadLength) & 0xFF);
			byte[] paymentRequestPayerPayload = new byte[paymentRequestPayerPayloadLength];
			for (int i=0; i<paymentRequestPayerPayloadLength; i++) {
				paymentRequestPayerPayload[i] = bytes[index++];
			}
			int paymentRequestPayerSignatureLength = bytes[index++] & 0xFF;
			byte[] paymentRequestPayerSignature = new byte[paymentRequestPayerSignatureLength];
			for (int i=0; i<paymentRequestPayerSignatureLength; i++) {
				paymentRequestPayerSignature[i] = bytes[index++];
			}
			
			byte[] paymentRequestPayer = new byte[paymentRequestPayerPayloadLength+paymentRequestPayerSignatureLength];
			int newIndex = 0;
			for (byte b : paymentRequestPayerPayload) {
				paymentRequestPayer[newIndex++] = b;
			}
			for (byte b : paymentRequestPayerSignature) {
				paymentRequestPayer[newIndex++] = b;
			}
			spr.paymentRequestPayer = PaymentRequest.decode(paymentRequestPayer);
			checkParameters(spr.version, spr.nofSignatures, spr.paymentRequestPayer);
			
			if (spr.nofSignatures > 1) {
				byte keyNumberPayee = bytes[index++];
				byte[] paymentRequestPayeePayload = paymentRequestPayerPayload;
				paymentRequestPayeePayload[paymentRequestPayeePayload.length-1] = keyNumberPayee;
				
				byte[] paymentRequestPayeeSignature = new byte[bytes.length - index];
				for (int i=0; i<paymentRequestPayeeSignature.length; i++) {
					paymentRequestPayeeSignature[i] = bytes[index++];
				}
				
				byte[] paymentRequestPayee = new byte[paymentRequestPayeePayload.length + paymentRequestPayeeSignature.length];
				newIndex = 0;
				for (byte b : paymentRequestPayeePayload) {
					paymentRequestPayee[newIndex++] = b;
				}
				for (byte b : paymentRequestPayeeSignature) {
					paymentRequestPayee[newIndex++] = b;
				}
				spr.paymentRequestPayee = PaymentRequest.decode(paymentRequestPayee);
				checkParameters(spr.version, spr.nofSignatures, spr.paymentRequestPayer, spr.paymentRequestPayee);
			}
			
			return spr;
		} catch (IndexOutOfBoundsException e) {
			throw new IllegalArgumentException("The given byte array is corrupt (not long enough).");
		}
	}
	
}
