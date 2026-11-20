package com.shotabra.web;

import java.util.Map;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.shotabra.game.GameError;
import com.shotabra.game.GameException;
import com.shotabra.game.PartyCodeGenerator;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** QR generado en el servidor (SVG) con la URL para unirse: {@code <url-pública>/j/<código>}. */
@RestController
public class QrController {

    private final PublicUrls urls;

    public QrController(PublicUrls urls) {
        this.urls = urls;
    }

    @GetMapping(value = "/api/parties/{code}/qr.svg", produces = "image/svg+xml")
    public ResponseEntity<String> qr(@PathVariable String code) throws WriterException {
        String normalized = PartyCodeGenerator.normalize(code);
        if (normalized == null) {
            throw new GameException(GameError.PARTY_NOT_FOUND);
        }
        BitMatrix m = new QRCodeWriter().encode(urls.joinUrl(normalized), BarcodeFormat.QR_CODE, 0, 0,
                Map.of(EncodeHintType.MARGIN, 2, EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("image/svg+xml"))
                .cacheControl(CacheControl.noStore())
                .body(toSvg(m));
    }

    static String toSvg(BitMatrix m) {
        StringBuilder path = new StringBuilder();
        for (int y = 0; y < m.getHeight(); y++) {
            for (int x = 0; x < m.getWidth(); x++) {
                if (m.get(x, y)) {
                    path.append('M').append(x).append(',').append(y).append("h1v1h-1z");
                }
            }
        }
        return """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %d %d" shape-rendering="crispEdges">\
                <rect width="100%%" height="100%%" fill="#fff"/><path fill="#000" d="%s"/></svg>"""
                .formatted(m.getWidth(), m.getHeight(), path);
    }
}
