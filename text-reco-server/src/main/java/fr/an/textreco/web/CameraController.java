package fr.an.textreco.web;

import fr.an.textreco.model.CameraDevice;
import fr.an.textreco.service.ProcessingService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cameras")
@CrossOrigin
public class CameraController {

    private final ProcessingService processingService;

    public CameraController(ProcessingService processingService) {
        this.processingService = processingService;
    }

    @GetMapping
    public List<CameraDevice> getCameras() {
        return processingService.getAvailableCameras();
    }
}
