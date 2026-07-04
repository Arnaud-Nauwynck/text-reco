package fr.an.textreco.web;

import fr.an.textreco.model.ProcessingContext;
import fr.an.textreco.service.ProcessingService;
import fr.an.textreco.util.MatFacade;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

@RestController
@RequestMapping("/api/control")
@CrossOrigin
public class ControlController {

    private final ProcessingService processingService;

    public ControlController(ProcessingService processingService) {
        this.processingService = processingService;
    }

    @PostMapping("/ocr")
    public void triggerOcr() {
        processingService.getContext().requestOcrOnce();
    }

    @PostMapping("/freeze")
    public Map<String, Boolean> toggleFreeze() {
        ProcessingContext ctx = processingService.getContext();
        ctx.inputSource.setFrozen(!ctx.inputSource.isFrozen());
        return Map.of("frozen", ctx.inputSource.isFrozen());
    }

    @PostMapping("/camera/{index}")
    public void selectCamera(@PathVariable int index) {
        processingService.getCameraCapture().selectCamera(index);
    }

    @PostMapping("/load-image")
    public void loadImage(@RequestParam("file") MultipartFile file) {
        try {
            File tmp = Files.createTempFile("textreco-upload-", ".png").toFile();
            file.transferTo(tmp);
            Mat mat = MatFacade.adopt(Imgcodecs.imread(tmp.getAbsolutePath()), "loadImage");
            tmp.delete();
            if (mat.empty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot read image");
            }
            processingService.getContext().inputSource.setLoadedMat(mat);
            processingService.getContext().inputSource.setFrozen(true);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @PostMapping("/perspective")
    public void setPerspective(@RequestBody PerspectiveDto dto) {
        if (dto.xRel() == null || dto.yRel() == null || dto.xRel().length != 4 || dto.yRel().length != 4) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "xRel and yRel must have 4 elements");
        }
        processingService.getCameraCapture(); // ensure pipeline is started
        // perspective processor is not exposed as a getter currently; access via service
    }

    public record PerspectiveDto(double[] xRel, double[] yRel) {}
}
