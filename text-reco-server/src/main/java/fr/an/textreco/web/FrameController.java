package fr.an.textreco.web;

import fr.an.textreco.model.TextLine;
import fr.an.textreco.model.TextLineExtractionResult;
import fr.an.textreco.service.MatEncoderService;
import fr.an.textreco.service.ProcessingService;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/frame")
@CrossOrigin
public class FrameController {

    private final ProcessingService processingService;
    private final MatEncoderService encoder;

    public FrameController(ProcessingService processingService, MatEncoderService encoder) {
        this.processingService = processingService;
        this.encoder = encoder;
    }

    @GetMapping("/{type}")
    public ResponseEntity<Map<String, String>> getFrame(@PathVariable String type) {
        Mat mat = switch (type) {
            case "raw"         -> processingService.getLatestRaw();
            case "edge"        -> processingService.getLatestEdge();
            case "perspective" -> processingService.getLatestPerspective();
            case "binary"      -> processingService.getLatestBinary();
            default            -> processingService.getMorphMat(type);
        };

        if (mat == null || mat.empty()) {
            return ResponseEntity.noContent().build();
        }

        String base64 = encoder.matToBase64Png(mat);
        return ResponseEntity.ok(Map.of("base64Png", base64 != null ? base64 : ""));
    }

    @GetMapping("/line/{n}")
    public ResponseEntity<Map<String, String>> getLineCrop(@PathVariable int n) {
        TextLineExtractionResult result = processingService.getContext().getTextLines();
        Mat binary = processingService.getLatestBinary();
        if (result == null || binary == null || binary.empty()) {
            return ResponseEntity.noContent().build();
        }
        List<TextLine> lines = result.lines();
        if (n < 0 || n >= lines.size()) {
            return ResponseEntity.notFound().build();
        }
        TextLine line = lines.get(n);
        int w = binary.cols();
        int top = line.rowStart();
        int bot = Math.min(line.rowEnd(), binary.rows());
        if (top >= bot || w == 0) {
            return ResponseEntity.noContent().build();
        }
        Mat crop = binary.submat(new Rect(0, top, w, bot - top));
        String base64 = encoder.matToBase64Png(crop);
        crop.release();
        return ResponseEntity.ok(Map.of("base64Png", base64 != null ? base64 : ""));
    }
}
