package fr.umontpellier.iut.discordbot.lib;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class QrCodeService {

	private static final int DEFAULT_SIZE = 400;

	public byte[] generateQrCodeWithLogo(String data, BufferedImage logo) throws Exception {
		byte[] output = new byte[0];

		if (data == null || data.isBlank()) {
			throw new IllegalArgumentException();
		}

		Map<EncodeHintType, Object> hints = new HashMap<>();
		hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
		hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
		hints.put(EncodeHintType.MARGIN, 1);

		QRCodeWriter qrWriter = new QRCodeWriter();
		BitMatrix bitMatrix = qrWriter.encode(data, BarcodeFormat.QR_CODE, DEFAULT_SIZE, DEFAULT_SIZE, hints);

		MatrixToImageConfig config = new MatrixToImageConfig(0xFF000000, 0xFFFFFFFF);
		BufferedImage qrImage = MatrixToImageWriter.toBufferedImage(bitMatrix, config);

		boolean hasLogo = logo != null;
		if (hasLogo) {
			Graphics2D g2 = qrImage.createGraphics();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

			int logoSize = DEFAULT_SIZE / 5;
			int x = (DEFAULT_SIZE - logoSize) / 2;
			int y = (DEFAULT_SIZE - logoSize) / 2;

			g2.setColor(Color.WHITE);
			g2.fill(new RoundRectangle2D.Float(x - 4, y - 4, logoSize + 8, logoSize + 8, 12, 12));
			g2.drawImage(logo, x, y, logoSize, logoSize, null);
			g2.dispose();
		}

		try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
			boolean written = ImageIO.write(qrImage, "png", outputStream);
			if (written) {
				output = outputStream.toByteArray();
			}
		} catch (IOException exception) {
			throw exception;
		}

		return output;
	}
}