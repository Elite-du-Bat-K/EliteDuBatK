package fr.umontpellier.iut.discordbot.lib;

import fr.umontpellier.iut.discordbot.Bot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.reflections.Reflections;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ObjectManagerTest {

    @Test
    void everyConcreteObjectManagerSubclassCanBeInstantiated() {
        Reflections reflections = new Reflections("fr.umontpellier.iut.discordbot");
        List<Class<?>> managerTypes = new ArrayList<>();
        reflections.getSubTypesOf(ObjectManager.class).stream()
                .filter(cls -> !cls.isInterface() && !Modifier.isAbstract(cls.getModifiers()))
                .forEach(managerTypes::add);

        assertFalse(managerTypes.isEmpty(), "No ObjectManager subclasses were discovered");

        List<Executable> instantiationChecks = managerTypes.stream()
                .map(managerType -> (Executable) () -> {
                    Constructor<?> constructor = managerType.getConstructor(Bot.class);
                    Object manager = constructor.newInstance((Bot) null);
                    assertNotNull(manager, () -> "Instantiation returned null for " + managerType.getName());
                })
                .toList();

        assertAll("Every ObjectManager subclass should instantiate without crashing", instantiationChecks);
    }

}
