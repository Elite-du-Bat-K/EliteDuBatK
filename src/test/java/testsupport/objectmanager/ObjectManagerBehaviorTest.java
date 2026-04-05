package testsupport.objectmanager;

import fr.umontpellier.iut.discordbot.lib.ObjectManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ObjectManagerBehaviorTest {
    private static class TestServiceManager extends ObjectManager<TestService> {
        TestServiceManager(String name) {
            super("testsupport.objectmanager", TestService.class, new Object[]{name}, String.class);
        }

        List<TestService> objects() {
            return get();
        }
    }

    @Test
    void loadsOneConcreteImplementationAndKeepsTheListImmutable() {
        TestServiceManager manager = new TestServiceManager("alpha");

        List<TestService> objects = manager.objects();

        assertEquals(1, objects.size());
        assertEquals("alpha", ((WorkingTestService) objects.getFirst()).name());
        assertThrows(UnsupportedOperationException.class,
                () -> objects.add(new WorkingTestService("beta")));
    }
}

