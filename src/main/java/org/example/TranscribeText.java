package org.example;

import com.google.cloud.speech.v1.*;
import com.google.api.gax.longrunning.OperationFuture;
import java.io.IOException;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class TranscribeText {
    public static void main(String[] args) throws IOException, InterruptedException, ExecutionException {
        try (SpeechClient speechClient = SpeechClient.create()) {
            RecognitionConfig config = RecognitionConfig.newBuilder()
                    .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                    .setSampleRateHertz(24000)
                    .setLanguageCode("fr-FR")
                    .setEnableAutomaticPunctuation(true)
                    .setUseEnhanced(true)
                    .setModel("latest_long")
                    .build();

            String gcsUri = "gs://audiofileask/panier.wav";

            RecognitionAudio audio = RecognitionAudio.newBuilder()
                    .setUri(gcsUri)
                    .build();

            LongRunningRecognizeRequest request = LongRunningRecognizeRequest.newBuilder()
                    .setConfig(config)
                    .setAudio(audio)
                    .build();

            try {
                System.out.println("Démarrage de la transcription...");

                OperationFuture<LongRunningRecognizeResponse, LongRunningRecognizeMetadata> response =
                        speechClient.longRunningRecognizeAsync(request);

                LongRunningRecognizeResponse recognizeResponse = response.get(30, TimeUnit.MINUTES);

                StringBuilder fullTranscript = new StringBuilder();
                for (SpeechRecognitionResult result : recognizeResponse.getResultsList()) {
                    SpeechRecognitionAlternative alternative = result.getAlternativesList().get(0);
                    fullTranscript.append(alternative.getTranscript()).append(" ");
                }

                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                String fileName = "transcription_" + timestamp + ".txt";

                try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
                    writer.write(fullTranscript.toString().trim());
                    System.out.println("\nTranscription enregistrée avec succès dans le fichier: " + fileName);
                } catch (IOException e) {
                    System.err.println("Erreur lors de l'écriture du fichier : " + e.getMessage());
                    System.out.println("\nTranscription (non sauvegardée) :");
                    System.out.println(fullTranscript.toString().trim());
                }

            } catch (Exception e) {
                System.err.println("Erreur pendant la transcription : " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}