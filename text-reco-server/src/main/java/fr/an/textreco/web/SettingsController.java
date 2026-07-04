package fr.an.textreco.web;

import fr.an.textreco.model.*;
import fr.an.textreco.service.ProcessingService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@CrossOrigin
public class SettingsController {

    private final ProcessingService processingService;

    public SettingsController(ProcessingService processingService) {
        this.processingService = processingService;
    }

    @GetMapping
    public SettingsDto getSettings() {
        ProcessingContext ctx = processingService.getContext();
        return new SettingsDto(
                ctx.appSettings,
                ctx.edgeDetectorSettings,
                ctx.preProcessingSettings,
                ctx.gridDetectorSettings,
                ctx.gridDetectorMode,
                ctx.correlationGridDetectorSettings,
                ctx.gridForcedValues);
    }

    @PutMapping
    public void updateSettings(@RequestBody Map<String, Object> body) {
        ProcessingContext ctx = processingService.getContext();
        applyAppSettings(body, ctx.appSettings);
        applyEdgeSettings(body, ctx.edgeDetectorSettings);
        applyPreProcessingSettings(body, ctx.preProcessingSettings);
        applyGridDetectorSettings(body, ctx.gridDetectorSettings);
        applyCorrelationSettings(body, ctx.correlationGridDetectorSettings);
        applyGridForcedValues(body, ctx.gridForcedValues);
        if (body.containsKey("gridDetectorMode")) {
            try {
                ctx.gridDetectorMode = GridDetectorMode.valueOf(body.get("gridDetectorMode").toString());
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (body.containsKey("ocrEnabled")) {
            ctx.setOcrEnabled(Boolean.parseBoolean(body.get("ocrEnabled").toString()));
        }
    }

    private static void applyAppSettings(Map<String, Object> b, AppSettings s) {
        if (b.containsKey("darkTheme")) s.setDarkTheme(Boolean.parseBoolean(b.get("darkTheme").toString()));
    }

    private static void applyEdgeSettings(Map<String, Object> b, EdgeDetectorSettings s) {
        if (b.containsKey("cannyThreshold1")) s.setCannyThreshold1(toDouble(b.get("cannyThreshold1")));
        if (b.containsKey("cannyThreshold2")) s.setCannyThreshold2(toDouble(b.get("cannyThreshold2")));
    }

    private static void applyPreProcessingSettings(Map<String, Object> b, PreProcessingSettings s) {
        if (b.containsKey("binarizationMethod")) {
            try { s.setBinarizationMethod(BinarizationMethod.valueOf(b.get("binarizationMethod").toString())); }
            catch (IllegalArgumentException ignored) {}
        }
        if (b.containsKey("tophatRadius"))    s.setTophatRadius(toInt(b.get("tophatRadius")));
        if (b.containsKey("tophatThreshold")) s.setTophatThreshold(toInt(b.get("tophatThreshold")));
        if (b.containsKey("adaptiveBlock"))   s.setAdaptiveBlock(toInt(b.get("adaptiveBlock")));
        if (b.containsKey("adaptiveC"))       s.setAdaptiveC(toInt(b.get("adaptiveC")));
        if (b.containsKey("seHalfLen"))       s.setSeHalfLen(toInt(b.get("seHalfLen")));
    }

    private static void applyGridDetectorSettings(Map<String, Object> b, GridDetectorSettings s) {
        if (b.containsKey("minLineH"))        s.setMinLineH(toInt(b.get("minLineH")));
        if (b.containsKey("maxLineH"))        s.setMaxLineH(toInt(b.get("maxLineH")));
        if (b.containsKey("minCharW"))        s.setMinCharW(toInt(b.get("minCharW")));
        if (b.containsKey("maxCharW"))        s.setMaxCharW(toInt(b.get("maxCharW")));
        if (b.containsKey("forceLineH"))      s.setForceLineH(toBool(b.get("forceLineH")));
        if (b.containsKey("forcedLineH"))     s.setForcedLineH(toDouble(b.get("forcedLineH")));
        if (b.containsKey("forceCharWidth"))  s.setForceCharWidth(toBool(b.get("forceCharWidth")));
        if (b.containsKey("forcedCharWPx"))   s.setForcedCharWPx(toDouble(b.get("forcedCharWPx")));
        if (b.containsKey("forceLineY0"))     s.setForceLineY0(toBool(b.get("forceLineY0")));
        if (b.containsKey("forcedLineY0"))    s.setForcedLineY0(toDouble(b.get("forcedLineY0")));
        if (b.containsKey("forceCharX0"))     s.setForceCharX0(toBool(b.get("forceCharX0")));
        if (b.containsKey("forcedCharX0"))    s.setForcedCharX0(toDouble(b.get("forcedCharX0")));
    }

    private static void applyCorrelationSettings(Map<String, Object> b, CorrelationGridDetectorSettings s) {
        if (b.containsKey("minLineHeight"))      s.setMinLineHeight(toInt(b.get("minLineHeight")));
        if (b.containsKey("maxLineHeight"))      s.setMaxLineHeight(toInt(b.get("maxLineHeight")));
        if (b.containsKey("minCharWidth"))       s.setMinCharWidth(toInt(b.get("minCharWidth")));
        if (b.containsKey("maxCharWidth"))       s.setMaxCharWidth(toInt(b.get("maxCharWidth")));
        if (b.containsKey("forceLineHeight"))    s.setForceLineHeight(toBool(b.get("forceLineHeight")));
        if (b.containsKey("forcedLineHeight"))   s.setForcedLineHeight(toDouble(b.get("forcedLineHeight")));
        if (b.containsKey("forceCharWidth2"))    s.setForceCharWidth(toBool(b.get("forceCharWidth2")));
        if (b.containsKey("forcedCharWidth"))    s.setForcedCharWidth(toDouble(b.get("forcedCharWidth")));
        if (b.containsKey("forceLineOffset"))    s.setForceLineOffset(toBool(b.get("forceLineOffset")));
        if (b.containsKey("forcedLineOffset"))   s.setForcedLineOffset(toDouble(b.get("forcedLineOffset")));
        if (b.containsKey("forceColOffset"))     s.setForceColOffset(toBool(b.get("forceColOffset")));
        if (b.containsKey("forcedColOffset"))    s.setForcedColOffset(toDouble(b.get("forcedColOffset")));
        if (b.containsKey("smoothingAlpha"))     s.setSmoothingAlpha(toDouble(b.get("smoothingAlpha")));
    }

    private static void applyGridForcedValues(Map<String, Object> b, GridDetectCoordModel m) {
        if (b.containsKey("lineHeightForce"))        m.lineHeight.setForce(toBool(b.get("lineHeightForce")));
        if (b.containsKey("lineHeightForcedValue"))  m.lineHeight.setForcedValue(toDouble(b.get("lineHeightForcedValue")));
        if (b.containsKey("charWidthForce"))         m.charWidth.setForce(toBool(b.get("charWidthForce")));
        if (b.containsKey("charWidthForcedValue"))   m.charWidth.setForcedValue(toDouble(b.get("charWidthForcedValue")));
        if (b.containsKey("lineOffsetForce"))        m.lineOffset.setForce(toBool(b.get("lineOffsetForce")));
        if (b.containsKey("lineOffsetForcedValue"))  m.lineOffset.setForcedValue(toDouble(b.get("lineOffsetForcedValue")));
        if (b.containsKey("charOffsetForce"))        m.charOffset.setForce(toBool(b.get("charOffsetForce")));
        if (b.containsKey("charOffsetForcedValue"))  m.charOffset.setForcedValue(toDouble(b.get("charOffsetForcedValue")));
    }

    private static double toDouble(Object v) {
        return v instanceof Number n ? n.doubleValue() : Double.parseDouble(v.toString());
    }

    private static int toInt(Object v) {
        return v instanceof Number n ? n.intValue() : Integer.parseInt(v.toString());
    }

    private static boolean toBool(Object v) {
        return Boolean.parseBoolean(v.toString());
    }

    // DTO carrying all settings for serialization
    public record SettingsDto(
            AppSettings app,
            EdgeDetectorSettings edge,
            PreProcessingSettings preProcessing,
            GridDetectorSettings gridDetector,
            GridDetectorMode gridDetectorMode,
            CorrelationGridDetectorSettings correlationGridDetector,
            GridDetectCoordModel gridForcedValues) {
    }
}
