package org.briefkastenscout.ml;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.briefkastenscout.model.BriefkastenRecord;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * On-Device Objekterkennung für Briefkästen (amenity=post_box) mittels
 * eines Ultralytics YOLO TensorFlow-Lite-Modells (briefkasten_detector.tflite).
 * Die Inferenz erfolgt asynchron im Hintergrund (ExecutorService).
 * Fehlerhafte Modelle oder Inferenzprobleme führen niemals zu einem Absturz,
 * sondern werden strikt als VISUAL_ERROR zurückgemeldet.
 */
public class BriefkastenDetector {

    private static final String TAG = "BriefkastenDetector";
    private static final String MODEL_FILE = "briefkasten_detector.tflite";

    // Standard YOLO-Konfiguration
    private static final int INPUT_SIZE = 640;             // 640x640 Input-Größe
    private static final float CONFIDENCE_THRESHOLD = 0.25f; // Mindestkonfidenz
    private static final float IOU_THRESHOLD = 0.45f;        // NMS IoU Schwellwert

    public interface DetectionCallback {
        void onDetectionFinished(long recordId, String visualStatus, Float confidence, String errorMessage);
    }

    private Interpreter interpreter;
    private GpuDelegate gpuDelegate;
    private final ExecutorService executorService;
    private final Handler mainHandler;
    private boolean isModelLoaded = false;
    private String loadErrorMessage = null;

    public BriefkastenDetector(Context context) {
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());

        // Modell asynchron/beim Start vorbereiten
        executorService.execute(() -> initInterpreter(context.getApplicationContext()));
    }

    /**
     * Initialisiert den TFLite Interpreter (GPU falls kompatibel, sonst CPU Fallback).
     */
    private synchronized void initInterpreter(Context context) {
        try {
            ByteBuffer modelBuffer = loadModelFile(context, MODEL_FILE);
            if (modelBuffer == null) {
                loadErrorMessage = "Modelldatei " + MODEL_FILE + " in assets nicht gefunden oder unlesbar.";
                Log.w(TAG, loadErrorMessage);
                return;
            }

            Interpreter.Options options = new Interpreter.Options();

            // Versuch 1: GPU Delegate aktivieren, falls Gerät kompatibel
            try {
                CompatibilityList compatList = new CompatibilityList();
                if (compatList.isDelegateSupportedOnThisDevice()) {
                    GpuDelegate.Options delegateOptions = compatList.getBestOptionsForThisDevice();
                    gpuDelegate = new GpuDelegate(delegateOptions);
                    options.addDelegate(gpuDelegate);
                    Log.i(TAG, "TFLite GPU-Delegate erfolgreich aktiviert.");
                } else {
                    options.setNumThreads(4);
                    Log.i(TAG, "TFLite nutzt CPU-Fallback (4 Threads).");
                }
            } catch (Throwable t) {
                Log.w(TAG, "GPU Delegate konnte nicht initialisiert werden, nutze CPU: " + t.getMessage());
                options.setNumThreads(4);
            }

            interpreter = new Interpreter(modelBuffer, options);
            isModelLoaded = true;
            Log.i(TAG, "BriefkastenDetector Modell erfolgreich geladen.");

        } catch (Throwable e) {
            loadErrorMessage = "Fehler beim Laden des TFLite-Modells: " + e.getMessage();
            Log.e(TAG, loadErrorMessage, e);
            isModelLoaded = false;
        }
    }

    /**
     * Lädt das Modell aus dem assets-Ordner in ein Direct ByteBuffer.
     */
    private ByteBuffer loadModelFile(Context context, String modelPath) {
        try (AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelPath);
             FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
             FileChannel fileChannel = inputStream.getChannel()) {
            long startOffset = fileDescriptor.getStartOffset();
            long declaredLength = fileDescriptor.getDeclaredLength();
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        } catch (Exception e) {
            Log.w(TAG, "Konnte " + modelPath + " nicht direkt mappen: " + e.getMessage());
            return null;
        }
    }

    /**
     * Führt die Bilderkennung asynchron auf dem Hintergrundthread aus.
     *
     * @param recordId Die Datensatz-ID zur Zuordnung.
     * @param photo    Das aufgenommene Foto als Bitmap.
     * @param callback Rückruf auf dem Main-Thread.
     */
    public void detect(long recordId, Bitmap photo, DetectionCallback callback) {
        executorService.execute(() -> {
            // 1. Prüfen, ob Modell geladen ist
            if (!isModelLoaded || interpreter == null) {
                String err = loadErrorMessage != null ? loadErrorMessage : "TFLite-Modell nicht geladen.";
                postResult(callback, recordId, BriefkastenRecord.VISUAL_ERROR, null, err);
                return;
            }

            if (photo == null) {
                postResult(callback, recordId, BriefkastenRecord.VISUAL_ERROR, null, "Eingabebild ist null.");
                return;
            }

            try {
                // 2. Vorverarbeitung: Skalieren & Normalisieren auf 640x640 Float32 [0..1]
                Bitmap resizedBitmap = Bitmap.createScaledBitmap(photo, INPUT_SIZE, INPUT_SIZE, true);
                ByteBuffer inputBuffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4);
                inputBuffer.order(ByteOrder.nativeOrder());
                inputBuffer.rewind();

                int[] intValues = new int[INPUT_SIZE * INPUT_SIZE];
                resizedBitmap.getPixels(intValues, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);

                // Tensor-Layout dynamisch bestimmen: manche TFLite/LiteRT-Exporte (insbesondere
                // der neuere "litert_torch"-Exportpfad) behalten PyTorchs Channels-First-Layout
                // (NCHW: [1,3,640,640]) bei, statt auf das klassische TFLite-NHWC ([1,640,640,3])
                // zu transponieren. Falsches Layout führt NICHT zu einem Lade-/Shape-Fehler
                // (ByteBuffer hat keine Shape-Prüfung), sondern nur zu unbrauchbaren/verzerrten
                // Erkennungsergebnissen ohne erkennbaren Fehler.
                int[] inputShape = interpreter.getInputTensor(0).shape();
                boolean isChannelsFirst = inputShape.length == 4 && inputShape[1] == 3;

                if (isChannelsFirst) {
                    // NCHW: erst alle R-Werte aller Pixel, dann alle G-, dann alle B-Werte
                    for (int pixelValue : intValues) {
                        inputBuffer.putFloat(((pixelValue >> 16) & 0xFF) / 255.0f);
                    }
                    for (int pixelValue : intValues) {
                        inputBuffer.putFloat(((pixelValue >> 8) & 0xFF) / 255.0f);
                    }
                    for (int pixelValue : intValues) {
                        inputBuffer.putFloat((pixelValue & 0xFF) / 255.0f);
                    }
                } else {
                    // NHWC: R, G, B direkt hintereinander pro Pixel
                    for (int pixelValue : intValues) {
                        inputBuffer.putFloat(((pixelValue >> 16) & 0xFF) / 255.0f);
                        inputBuffer.putFloat(((pixelValue >> 8) & 0xFF) / 255.0f);
                        inputBuffer.putFloat((pixelValue & 0xFF) / 255.0f);
                    }
                }

                // 3. Output-Buffer vorbereiten (YOLOv8/v9/v11 Shape z.B. [1, 5, 8400] oder [1, 8400, 5])
                int[] outputShape = interpreter.getOutputTensor(0).shape();
                float[][][] outputArray;
                boolean transposed = false; // ob shape [1, 5, 8400] oder [1, 8400, 5]

                int dim1 = outputShape.length > 1 ? outputShape[1] : 5;
                int dim2 = outputShape.length > 2 ? outputShape[2] : 8400;

                outputArray = new float[1][dim1][dim2];

                // 4. Inferenz ausführen
                interpreter.run(inputBuffer, outputArray);

                // 5. Postprocessing: Bounding Boxes, Class Scores & Non-Max-Suppression (NMS)
                List<DetectionBox> candidateBoxes = new ArrayList<>();
                float highestConfidence = 0.0f;

                if (dim1 < dim2) {
                    // Format [1, 4 + classes, 8400] (Ultralytics YOLOv8 Standard)
                    int numBoxes = dim2;
                    int numClasses = dim1 - 4;
                    for (int i = 0; i < numBoxes; i++) {
                        float cx = outputArray[0][0][i];
                        float cy = outputArray[0][1][i];
                        float w = outputArray[0][2][i];
                        float h = outputArray[0][3][i];

                        // Maximale Klassenkonfidenz (bei 1 Klasse einfach class 0)
                        float maxScore = 0.0f;
                        for (int c = 0; c < numClasses; c++) {
                            float score = outputArray[0][4 + c][i];
                            if (score > maxScore) maxScore = score;
                        }

                        if (maxScore > highestConfidence) {
                            highestConfidence = maxScore;
                        }

                        if (maxScore >= CONFIDENCE_THRESHOLD) {
                            candidateBoxes.add(new DetectionBox(cx, cy, w, h, maxScore));
                        }
                    }
                } else {
                    // Format [1, 8400, 4 + classes]
                    int numBoxes = dim1;
                    int numClasses = dim2 - 4;
                    for (int i = 0; i < numBoxes; i++) {
                        float cx = outputArray[0][i][0];
                        float cy = outputArray[0][i][1];
                        float w = outputArray[0][i][2];
                        float h = outputArray[0][i][3];

                        float maxScore = 0.0f;
                        for (int c = 0; c < numClasses; c++) {
                            float score = outputArray[0][i][4 + c];
                            if (score > maxScore) maxScore = score;
                        }

                        if (maxScore > highestConfidence) {
                            highestConfidence = maxScore;
                        }

                        if (maxScore >= CONFIDENCE_THRESHOLD) {
                            candidateBoxes.add(new DetectionBox(cx, cy, w, h, maxScore));
                        }
                    }
                }

                // NMS anwenden
                List<DetectionBox> nmsResults = applyNMS(candidateBoxes, IOU_THRESHOLD);

                if (!nmsResults.isEmpty()) {
                    float bestScore = nmsResults.get(0).score;
                    postResult(callback, recordId, BriefkastenRecord.VISUAL_DETECTED, bestScore, null);
                } else {
                    // Erfolgreich geprüft, aber kein Objekt über Threshold
                    postResult(callback, recordId, BriefkastenRecord.VISUAL_NOT_DETECTED, highestConfidence > 0 ? highestConfidence : 0.0f, null);
                }

            } catch (Throwable t) {
                Log.e(TAG, "Inferenzfehler: " + t.getMessage(), t);
                postResult(callback, recordId, BriefkastenRecord.VISUAL_ERROR, null, "Inferenzfehler: " + t.getMessage());
            }
        });
    }

    /**
     * Non-Maximum Suppression (NMS) zur Eliminierung überlappender Bounding Boxes.
     */
    private List<DetectionBox> applyNMS(List<DetectionBox> boxes, float iouThreshold) {
        List<DetectionBox> result = new ArrayList<>();
        Collections.sort(boxes, (a, b) -> Float.compare(b.score, a.score));

        boolean[] suppressed = new boolean[boxes.size()];
        for (int i = 0; i < boxes.size(); i++) {
            if (suppressed[i]) continue;
            DetectionBox current = boxes.get(i);
            result.add(current);

            for (int j = i + 1; j < boxes.size(); j++) {
                if (suppressed[j]) continue;
                if (calculateIoU(current, boxes.get(j)) > iouThreshold) {
                    suppressed[j] = true;
                }
            }
        }
        return result;
    }

    private float calculateIoU(DetectionBox a, DetectionBox b) {
        float x1 = Math.max(a.x1(), b.x1());
        float y1 = Math.max(a.y1(), b.y1());
        float x2 = Math.min(a.x2(), b.x2());
        float y2 = Math.min(a.y2(), b.y2());

        float intersectionArea = Math.max(0, x2 - x1) * Math.max(0, y2 - y1);
        float areaA = a.w * a.h;
        float areaB = b.w * b.h;

        float unionArea = areaA + areaB - intersectionArea;
        if (unionArea <= 0) return 0f;
        return intersectionArea / unionArea;
    }

    private void postResult(DetectionCallback callback, long recordId, String status, Float conf, String error) {
        mainHandler.post(() -> {
            if (callback != null) {
                callback.onDetectionFinished(recordId, status, conf, error);
            }
        });
    }

    public void shutdown() {
        executorService.execute(() -> {
            if (interpreter != null) {
                try {
                    interpreter.close();
                } catch (Exception ignored) {}
            }
            if (gpuDelegate != null) {
                try {
                    gpuDelegate.close();
                } catch (Exception ignored) {}
            }
        });
        executorService.shutdown();
    }

    /**
     * Hilfsklasse für Bounding-Boxen
     */
    private static class DetectionBox {
        final float cx, cy, w, h, score;

        DetectionBox(float cx, float cy, float w, float h, float score) {
            this.cx = cx;
            this.cy = cy;
            this.w = w;
            this.h = h;
            this.score = score;
        }

        float x1() { return cx - w / 2f; }
        float y1() { return cy - h / 2f; }
        float x2() { return cx + w / 2f; }
        float y2() { return cy + h / 2f; }
    }
}
