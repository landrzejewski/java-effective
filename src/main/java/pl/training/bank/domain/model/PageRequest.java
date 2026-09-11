package pl.training.bank.domain.model;

public record PageRequest(int index, int size) {

    public int offset() {
        return index * size;
    }

}
