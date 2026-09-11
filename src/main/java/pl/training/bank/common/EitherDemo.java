package pl.training.bank.common;

/* Runnable demonstration of Either. Kept separate from the type itself: a source file that declares
   top-level methods is a compact source file, and those may not carry a package declaration. */
public final class EitherDemo {

    private EitherDemo() {}

    public static Either<String, Double> divide(final double value, final double factor) {
        if (factor == 0) {
            return Either.error("Divide by zero");
        }
        return Either.success(value / factor);
    }

    public static void main(String[] args) {
        var finalResult = divide(10, 0)
                .flatMap(result -> Either.success(result * 2))
                .mapError(IllegalStateException::new);

        switch (finalResult) {
            case Success<IllegalStateException, Double>(Double v) -> System.out.println("Final result is: " + v);
            case Failure<IllegalStateException, Double>(IllegalStateException e) -> System.out.println("Error: " + e);
        }
    }
}
