package br.com.droidboaoferta;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class ViaCepClient {
    private ViaCepClient() {
    }

    static Address fetch(String rawZipCode) throws Exception {
        String zipCode = onlyDigits(rawZipCode);
        if (zipCode.length() != 8) {
            throw new IllegalArgumentException("Invalid zip code");
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "https://viacep.com.br/ws/" + zipCode + "/json/"
        ).openConnection();
        connection.setConnectTimeout(8_000);
        connection.setReadTimeout(10_000);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "Alertou/1.0 Android");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("HTTP " + status);
            }
            JSONObject json = new JSONObject(readResponse(connection.getInputStream()));
            if (json.optBoolean("erro", false)) {
                throw new IllegalArgumentException("Zip code not found");
            }
            return new Address(
                    zipCode,
                    json.optString("logradouro", ""),
                    json.optString("bairro", ""),
                    json.optString("localidade", ""),
                    json.optString("uf", "")
            );
        } finally {
            connection.disconnect();
        }
    }

    static String onlyDigits(String value) {
        return value == null ? "" : value.replaceAll("\\D+", "");
    }

    private static String readResponse(InputStream input) throws Exception {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    static final class Address {
        private final String zipCode;
        private final String street;
        private final String neighborhood;
        private final String city;
        private final String state;

        Address(String zipCode, String street, String neighborhood, String city, String state) {
            this.zipCode = zipCode == null ? "" : zipCode.trim();
            this.street = street == null ? "" : street.trim();
            this.neighborhood = neighborhood == null ? "" : neighborhood.trim();
            this.city = city == null ? "" : city.trim();
            this.state = state == null ? "" : state.trim().toUpperCase();
        }

        String getZipCode() {
            return zipCode;
        }

        String getStreet() {
            return street;
        }

        String getNeighborhood() {
            return neighborhood;
        }

        String getCity() {
            return city;
        }

        String getState() {
            return state;
        }
    }
}
