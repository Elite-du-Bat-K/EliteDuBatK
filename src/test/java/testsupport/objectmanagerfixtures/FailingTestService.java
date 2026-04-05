package testsupport.objectmanagerfixtures;

public class FailingTestService implements TestService {
    public FailingTestService(String name, int version) {
        throw new IllegalStateException("boom: " + name + " " + version);
    }
}
