package fr.umontpellier.iut.discordbot.services;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class CryptoReceiptService {
	private static final String ALGORITHM = "Ed25519";
	private static final String KEY_DIR = "keys";
	private static final String PUB_KEY_FILE = KEY_DIR + "/public.pem";
	private static final String PRIV_KEY_FILE = KEY_DIR + "/private.pem";
	private static final String PEM_HEADER_PUB = "-----BEGIN PUBLIC KEY-----\n";
	private static final String PEM_FOOTER_PUB = "\n-----END PUBLIC KEY-----\n";
	private static final String PEM_HEADER_PRIV = "-----BEGIN PRIVATE KEY-----\n";
	private static final String PEM_FOOTER_PRIV = "\n-----END PRIVATE KEY-----\n";

	private PublicKey publicKey;
	private PrivateKey privateKey;

	public CryptoReceiptService() {
		this.initializeKeys();
	}

	private void initializeKeys() {
		File directory = new File(KEY_DIR);
		boolean dirExists = directory.exists();

		if (!dirExists) {
			dirExists = directory.mkdirs();
		}

		if (dirExists) {
			File pubFile = new File(PUB_KEY_FILE);
			File privFile = new File(PRIV_KEY_FILE);
			boolean keysExist = pubFile.exists() && privFile.exists();

			if (keysExist) {
				this.loadKeys(pubFile, privFile);
			}
			if (!keysExist) {
				this.generateAndSaveKeys(pubFile, privFile);
			}
		}

		if (!dirExists) {
			throw new RuntimeException("Failed to create key directory");
		}
	}

	private void loadKeys(File pubFile, File privFile) {
		try {
			String pubPem = Files.readString(pubFile.toPath(), StandardCharsets.UTF_8);
			String privPem = Files.readString(privFile.toPath(), StandardCharsets.UTF_8);

			String pubBase64 = pubPem.replace(PEM_HEADER_PUB, "").replace(PEM_FOOTER_PUB, "").replaceAll("\\s", "");
			String privBase64 = privPem.replace(PEM_HEADER_PRIV, "").replace(PEM_FOOTER_PRIV, "").replaceAll("\\s", "");

			KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM);
			this.publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pubBase64)));
			this.privateKey = keyFactory
					.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privBase64)));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void generateAndSaveKeys(File pubFile, File privFile) {
		try {
			KeyPairGenerator kpg = KeyPairGenerator.getInstance(ALGORITHM);
			KeyPair keyPair = kpg.generateKeyPair();
			this.publicKey = keyPair.getPublic();
			this.privateKey = keyPair.getPrivate();

			String pubBase64 = Base64.getMimeEncoder(64, new byte[] { '\n' })
					.encodeToString(this.publicKey.getEncoded());
			String privBase64 = Base64.getMimeEncoder(64, new byte[] { '\n' })
					.encodeToString(this.privateKey.getEncoded());

			Files.writeString(pubFile.toPath(), PEM_HEADER_PUB + pubBase64 + PEM_FOOTER_PUB, StandardCharsets.UTF_8);
			Files.writeString(privFile.toPath(), PEM_HEADER_PRIV + privBase64 + PEM_FOOTER_PRIV,
					StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void forceGenerateKeys() {
		File pubFile = new File(PUB_KEY_FILE);
		File privFile = new File(PRIV_KEY_FILE);
		this.generateAndSaveKeys(pubFile, privFile);
	}

	public String signPayload(String payload) {
		String base64Signature = "";
		boolean isReady = this.privateKey != null;

		if (isReady) {
			try {
				Signature sig = Signature.getInstance(ALGORITHM);
				sig.initSign(this.privateKey);
				sig.update(payload.getBytes(StandardCharsets.UTF_8));
				byte[] signatureBytes = sig.sign();
				base64Signature = Base64.getEncoder().encodeToString(signatureBytes);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		return base64Signature;
	}

	public boolean verifySignature(byte[] pubKeyBytes, String base64Signature, String payload) {
		boolean isValid = false;
		boolean hasInputs = pubKeyBytes != null && base64Signature != null && payload != null;

		if (hasInputs) {
			try {
				String pubPem = new String(pubKeyBytes, StandardCharsets.UTF_8);
				String pubBase64 = pubPem.replace(PEM_HEADER_PUB, "").replace(PEM_FOOTER_PUB, "").replaceAll("\\s", "");

				KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM);
				PublicKey externalPubKey = keyFactory
						.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pubBase64)));

				Signature sig = Signature.getInstance(ALGORITHM);
				sig.initVerify(externalPubKey);
				sig.update(payload.getBytes(StandardCharsets.UTF_8));

				byte[] signatureBytes = Base64.getDecoder().decode(base64Signature);
				isValid = sig.verify(signatureBytes);
			} catch (Exception ignored) {
				isValid = false;
			}
		}

		return isValid;
	}

	public File getPublicKeyFile() {
		return new File(PUB_KEY_FILE);
	}
}