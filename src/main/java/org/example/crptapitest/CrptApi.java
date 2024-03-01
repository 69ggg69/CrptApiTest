package org.example.crptapitest;

import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.*;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;

public class CrptApi {
    private final Semaphore semaphore;
    private static final Logger logger = Logger.getLogger(CrptApi.class.getName());
    private final ObjectMapper objectMapper;
    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.semaphore = new Semaphore(requestLimit, true);
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        this.objectMapper = new ObjectMapper();
        scheduler.scheduleAtFixedRate(this::resetSemaphore, 0, 1, TimeUnit.SECONDS);
    }

    public void createDocument(MyDocument document, String token) {
        try {
            semaphore.acquire();
            HttpURLConnection connection = createConnection("https://ismp.crpt.ru/api/v3/lk/documents/create", token);
            String jsonInputString = objectMapper.writeValueAsString(document);
            sendRequest(connection, jsonInputString);
            handleResponse(connection);
        } catch (IOException | InterruptedException e) {
            logger.log(Level.SEVERE, "An error occurred while sending document", e);
        } finally {
            semaphore.release();
        }
    }

    private HttpURLConnection createConnection(String urlString, String token) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        setAuthorizationHeader(connection, token);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        return connection;
    }

    private void setAuthorizationHeader(HttpURLConnection connection, String token) {
        connection.setRequestProperty("Authorization", "Bearer " + token);
    }

    private void sendRequest(HttpURLConnection connection, String jsonInputString) throws IOException {
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
    }

    private void handleResponse(HttpURLConnection connection) throws IOException {
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            logger.log(Level.SEVERE, "HTTP response code: " + responseCode);
        } else {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                logger.log(Level.INFO, "Response: " + response.toString());
            }
        }
    }
    private void resetSemaphore() {
        int permitsToRelease = semaphore.availablePermits();
        semaphore.drainPermits();
        semaphore.release(permitsToRelease);
    }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @Getter
    @Setter
    public static class MyDocument {
        private String description;
        private String doc_id;
        private String doc_status;
        private String doc_type;
        private boolean importRequest;
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private String production_date;
        private String production_type;
        private List<Product> products;
        private String reg_date;
        private String reg_number;
    }
    @Getter
    @Setter
    public static class Product {
        private String certificate_document;
        private String certificate_document_date;
        private String certificate_document_number;
        private String owner_inn;
        private String producer_inn;
        private String production_date;
        private String tnved_code;
        private String uit_code;
        private String uitu_code;
    }
    public static void main(String[] args) {
      CrptApi crptApi = new CrptApi(TimeUnit.SECONDS, 10);
      MyDocument myDocument = new MyDocument();
      crptApi.createDocument(myDocument, "token");
    }
}
