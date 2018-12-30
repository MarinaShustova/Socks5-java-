public class DomainException extends Exception {
    public DomainException() { super(); }
    public DomainException(String message) { super(message); }
    public DomainException(String message, Throwable cause) { super(message, cause); }
    public DomainException(Throwable cause) { super(cause); }
}
