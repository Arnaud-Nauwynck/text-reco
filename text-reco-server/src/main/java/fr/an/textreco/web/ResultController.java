package fr.an.textreco.web;

import fr.an.textreco.model.*;
import fr.an.textreco.service.ProcessingService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/result")
@CrossOrigin
public class ResultController {

    private final ProcessingService processingService;

    public ResultController(ProcessingService processingService) {
        this.processingService = processingService;
    }

    @GetMapping("/text")
    public Map<String, String> getText() {
        ProcessingContext ctx = processingService.getContext();
        return Map.of(
                "ocrText", nullToEmpty(ctx.getOcrText()),
                "tessOcrText", nullToEmpty(ctx.getTessOcrText()));
    }

    @GetMapping("/grid")
    public GridDetectionResult getGrid() {
        return processingService.getContext().getGridDetection();
    }

    @GetMapping("/corr-grid")
    public CorrelationGridDetectionResult getCorrGrid() {
        return processingService.getContext().getCorrelationGridDetection();
    }

    @GetMapping("/stats")
    public FrameStats getStats() {
        return processingService.getContext().getFrameStats();
    }

    @GetMapping("/lines")
    public TextLineExtractionResult getLines() {
        return processingService.getContext().getTextLines();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
