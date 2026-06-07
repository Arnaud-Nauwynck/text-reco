package fr.an.textreco.model;

public record CameraDevice(int index, String name, int width, int height, double fps) {

    public String label() {
        if (width > 0 && height > 0) {
            String fpsStr = fps > 0 ? String.format(" %.0ffps", fps) : "";
            return String.format("Camera %d  %dx%d%s", index, width, height, fpsStr);
        }
        return "Camera " + index;
    }

    @Override
    public String toString() {
        return label();
    }
}
