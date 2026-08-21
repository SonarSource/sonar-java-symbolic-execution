package symbolicexecution.checks;

public class ConditionAlwaysTrueOrFalseJakarta {
    class Foobar {
        private String x;
        private String y;
        @jakarta.validation.constraints.NotNull
        public String getX() {return x;}
        @javax.validation.constraints.NotNull
        private String getY() {return y;}
    }

    private void myMethod(Foobar source) {
        var x = source.getX(); // annotated with @NotNull
        if (x != null) { // as javax.validation.constraints.NotNull doesn't guarantee static nonnullability, this is not a violation
            // do something with x
        }
    }

    private void anotherMethod(Foobar source) {
        if (source.getX() != null) {
            //do something
        }
    }
}
