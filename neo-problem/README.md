# Neo Problem

## Requirements 
 * Uses JAX-RS
 * Agnostic to the concrete JAX-RS-implementation: Must not change or extend the official API (plus its implementation), which would prevent a switch to another JAXRS-implementation.
 * Thus must not change existing JAXRS-API, e.g. no new methods at Response-Class, no new provided Exceptiontypes to be thrown or handled by users.
 * Optional ExceptionMapper-Mechanism for mapping user-supplied exceptions to problems. Preferably both globally for the whole application or locally for single resources/methods.
 * ExceptionMapper-Mechanism must not require extensions to the official JAXRS-API, which would prevent a switch to another JAXRS-implementation. Implementing a JAXRS-ExceptionMapper is ok. Something like new annotations are not ok, because these would introduce a dependency on a concrete JAXRS-implementation.
 * ExceptionMapper-Mechanism must support arbitrary exceptions (Not just those deriving from some baseclass).
 * natural exception handling in try-catch-blocks are preferred to switching on exception-type with instanceof

## Proposals
 * Users may implement an JAXRS-ExceptionMapper for global mapping of user-supplied exceptions
 * Local mapping of exceptions for single resource-methods by reusable handlers. Unfortunately no automatic mapping for all methods inside a resource.
'''
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("job")
    public JobRepresentation schedule(List<ResourceRepresentation> resources) {
        return WrappableSupplier.of(() -> {
 
            return doSchedule(resources);

        }) //
                .wrappedBy(this::handleMyRuntimeExceptions) // custom exception handler usable for multiple resource methods
                .wrappedBy((wrapped) -> methodMetrics(wrapped, "schedule")) // optional chained wrapper: measuring metrics
                .get();
    }

    private <T> T handleMyRuntimeExceptions(Supplier<T> wrapped) {
        try {
            return wrapped.get();
        } catch(MyRuntimeException e) {
            throw buildProblemException(e);
        }
    }

    @FunctionalInterface
    private static interface WrappableSupplier<T> extends Supplier<T> {

        public static <T> WrappableSupplier<T> of(Supplier<T> s) {
            return () -> s.get();
        }

        default WrappableSupplier<T> wrappedBy(Function<Supplier<T>, T> f) {
            return () -> f.apply(this);
        }
    }
'''

