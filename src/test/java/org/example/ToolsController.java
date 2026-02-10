package org.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.UUID;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;



import com.google.gson.Gson;

import org.example.APISEGToken;

public class ToolsController {




    



    public String getAPISEGToken() throws ProtocolException, MalformedURLException, IOException {

       String apiEndPoint = "https://apiseg.dev.telefonica.es:21443/openid/connect/auth/oauth/v2/t3/org/token";
        String keystorePath = "GIM.p12"; // Your keystore file name in the resources folder
        String keystorePassword = "gim_rcs"; // Your keystore password
        String keyPassword = "gim_rcs"; // Your key password

        HttpsURLConnection connection = null;

        try {
            // Load the client certificate into a KeyStore from the resources folder
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (InputStream is = ToolsController.class.getClassLoader().getResourceAsStream(keystorePath)) {
                if (is == null) {
                    throw new IllegalArgumentException("Keystore file not found in resources: " + keystorePath);
                }
                keyStore.load(is, keystorePassword.toCharArray());
            }

            // Create a KeyManagerFactory and initialize it with the KeyStore
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, keyPassword.toCharArray());

            // Create an SSLContext and initialize it with the KeyManagerFactory
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagerFactory.getKeyManagers(), null, null);

            // Create a URL object
            URL url = new URL(apiEndPoint);
            // Open a connection
            connection = (HttpsURLConnection) url.openConnection();
            // Set the SSL context's socket factory on the connection
            connection.setSSLSocketFactory(sslContext.getSocketFactory());
            // Set the request method to POST
            connection.setRequestMethod("POST");
            // Set the request headers
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            // Enable input and output streams
            connection.setDoOutput(true);

            // Create the request body
            String urlParameters = "client_id=9a37bb00-8665-479e-bfa0-73a252aa1c78&client_secret=6de34436-22a4-4ebe-bf9e-d68690ffe7dd&grant_type=CERT";

            // Write the request body
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = urlParameters.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Get the response code
            int responseCode = connection.getResponseCode();
            System.out.println("Response Code: " + responseCode);

            // Read the response
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                System.out.println("Response: " + response.toString());
                 Gson gson = new Gson();
               APISEGToken apisegToken = gson.fromJson(response.toString(), APISEGToken.class);

               System.out.println("api seg token: " + apisegToken.getAccessToken());
               return apisegToken.getAccessToken();
            }

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }




    }


