package fr.an.textreco.model;

public record TextLine(int rowStart, int rowEnd) {

    public int height() {
        return rowEnd - rowStart;
    }
}
