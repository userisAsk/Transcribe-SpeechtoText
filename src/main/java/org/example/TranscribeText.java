package org.example;

import com.google.cloud.speech.v1.*;
import com.google.api.gax.longrunning.OperationFuture;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

import java.io.IOException;
import java.io.File;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class TranscribeText {
    public static void main(String[] args) throws IOException, InterruptedException, ExecutionException {
        // Nom du bucket et chemin local du dossier
        String bucketName = "audiofileask"; // Remplacez par le nom de votre bucket
        String localFolderPath = "C:/Ecole/3eme année/transcribe/transcribeproject/AudioFile"; // Chemin vers le dossier local contenant les fichiers audio
        String transcriptionFolderPath = "C:/Ecole/3eme année/transcribe/transcribeproject/transcription_terminer"; // Chemin vers le dossier de transcription

        // Créer le dossier de transcription s'il n'existe pas
        File transcriptionFolder = new File(transcriptionFolderPath);
        if (!transcriptionFolder.exists()) {
            if (transcriptionFolder.mkdir()) {
                System.out.println("Dossier de transcription créé : " + transcriptionFolderPath);
            } else {
                System.err.println("Impossible de créer le dossier de transcription.");
                return;
            }
        }

        // Instancier le client Google Cloud Storage
        Storage storage = StorageOptions.getDefaultInstance().getService();

        // Charger les fichiers dans le bucket
        File folder = new File(localFolderPath);
        if (folder.isDirectory()) {
            for (File file : folder.listFiles()) {
                if (file.isFile()) {
                    String objectName = file.getName(); // Nom de l'objet dans le bucket
                    BlobId blobId = BlobId.of(bucketName, objectName);
                    BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();

                    // Charger le fichier dans le bucket
                    storage.create(blobInfo, Files.readAllBytes(Paths.get(file.getAbsolutePath())));
                    System.out.println("Fichier ajouté au bucket : " + objectName);
                }
            }
        } else {
            System.err.println("Le chemin spécifié n'est pas un dossier.");
            return;
        }

        // Instancier le client Speech-to-Text
        try (SpeechClient speechClient = SpeechClient.create()) {
            // Configuration de la reconnaissance
            RecognitionConfig config = RecognitionConfig.newBuilder()
                    .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                    .setSampleRateHertz(24000)
                    .setLanguageCode("fr-FR")
                    .setEnableAutomaticPunctuation(true)
                    .setUseEnhanced(true)
                    .setModel("latest_long")
                    .build();

            // Parcourir les fichiers audio dans le bucket
            for (File file : folder.listFiles()) {
                if (file.isFile()) {
                    String gcsUri = "gs://" + bucketName + "/" + file.getName();

                    // Créer la requête de reconnaissance audio
                    RecognitionAudio audio = RecognitionAudio.newBuilder()
                            .setUri(gcsUri)
                            .build();

                    LongRunningRecognizeRequest request = LongRunningRecognizeRequest.newBuilder()
                            .setConfig(config)
                            .setAudio(audio)
                            .build();

                    try {
                        System.out.println("Démarrage de la transcription pour : " + file.getName());

                        OperationFuture<LongRunningRecognizeResponse, LongRunningRecognizeMetadata> response =
                                speechClient.longRunningRecognizeAsync(request);

                        LongRunningRecognizeResponse recognizeResponse = response.get(30, TimeUnit.MINUTES);

                        StringBuilder fullTranscript = new StringBuilder();
                        for (SpeechRecognitionResult result : recognizeResponse.getResultsList()) {
                            SpeechRecognitionAlternative alternative = result.getAlternativesList().get(0);
                            String transcript = alternative.getTranscript();

                            // Insérer des sauts de ligne après chaque phrase
                            transcript = transcript.replaceAll("([\\.\\!\\?])\\s+", "$1\n");
                            fullTranscript.append(transcript).append("\n");
                        }

                        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                        String transcribedFileName = "transcription_" + file.getName() + "_" + timestamp + ".txt";

                        // Chemin complet vers le fichier de transcription dans le dossier transcription_terminer
                        Path transcribedFilePath = Paths.get(transcriptionFolderPath, transcribedFileName);

                        try (BufferedWriter writer = new BufferedWriter(new FileWriter(transcribedFilePath.toFile()))) {
                            writer.write(fullTranscript.toString().trim());
                            System.out.println("\nTranscription enregistrée avec succès dans le fichier: " + transcribedFilePath);
                        } catch (IOException e) {
                            System.err.println("Erreur lors de l'écriture du fichier : " + e.getMessage());
                            System.out.println("\nTranscription (non sauvegardée) :");
                            System.out.println(fullTranscript.toString().trim());
                        }

                    } catch (Exception e) {
                        System.err.println("Erreur pendant la transcription de " + file.getName() + " : " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
