package com.shotabra.web;

import com.shotabra.config.AppProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * URL pública de la app. Si {@code APP_PUBLIC_URL} no está definida se usa la de la petición actual
 * (respetando X-Forwarded-*), así el QR funciona tanto en la WiFi de casa como detrás de Coolify.
 */
@Component
public class PublicUrls {

    private final AppProperties props;

    public PublicUrls(AppProperties props) {
        this.props = props;
    }

    public String base() {
        if (!props.publicUrl().isBlank()) {
            return props.publicUrl();
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }

    public String joinUrl(String code) {
        return base() + "/j/" + code;
    }
}
