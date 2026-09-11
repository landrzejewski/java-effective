package pl.training.dsl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pl.training.dsl.Mod005CombinatorDsl.Address;
import static pl.training.dsl.Mod005CombinatorDsl.RegisterUser;
import static pl.training.dsl.Mod005CombinatorDsl.Rule;
import static pl.training.dsl.Mod005CombinatorDsl.USER_RULES;
import static pl.training.dsl.Mod005CombinatorDsl.Violation;
import static pl.training.dsl.Mod005CombinatorDsl.all;
import static pl.training.dsl.Mod005CombinatorDsl.between;
import static pl.training.dsl.Mod005CombinatorDsl.inSet;
import static pl.training.dsl.Mod005CombinatorDsl.matches;
import static pl.training.dsl.Mod005CombinatorDsl.minLength;
import static pl.training.dsl.Mod005CombinatorDsl.notBlank;
import static pl.training.dsl.Mod005CombinatorDsl.on;

class Mod005CombinatorDslTest {

    private static RegisterUser user(String email, String password, int age, String country,
                                     Address address, Optional<String> guardian) {
        return new RegisterUser(email, password, age, country, address, guardian);
    }

    private static RegisterUser valid() {
        return user("alice@example.com", "p4ssw0rd", 30, "PL", new Address("Warsaw", "00-001"), Optional.empty());
    }

    private static List<String> paths(List<Violation> violations) {
        return violations.stream().map(Violation::field).toList();
    }

    @Test
    @DisplayName("a valid object produces no violations")
    void validObjectPasses() {
        assertEquals(List.of(), USER_RULES.check(valid()));
    }

    @Test
    @DisplayName("every rule runs — nothing short-circuits at the first failure")
    void collectsAllViolations() {
        Rule<String> password = notBlank().and(minLength(8)).and(matches(Pattern.compile("\\w+")));

        assertEquals(3, password.check("").size(),
                "blank, too short and non-matching must all be reported in one pass");
    }

    @Test
    @DisplayName("problems across different fields are all reported together")
    void reportsAcrossFields() {
        var broken = user("not-an-email", "abc", 30, "PL", new Address("Warsaw", "00-001"), Optional.empty());

        assertEquals(List.of("email", "password"), paths(USER_RULES.check(broken)));
    }

    @Test
    @DisplayName("on() composes paths, so a nested validator reports address.zipCode")
    void composesFieldPaths() {
        var broken = user("alice@example.com", "p4ssw0rd", 30, "PL", new Address("", "bad"), Optional.empty());

        assertEquals(List.of("address.city", "address.zipCode"), paths(USER_RULES.check(broken)));
    }

    @Test
    @DisplayName("when() applies a rule only while its condition holds")
    void conditionalRuleFiresOnlyWhenRelevant() {
        var address = new Address("Warsaw", "00-001");
        var adult = user("alice@example.com", "p4ssw0rd", 30, "PL", address, Optional.empty());
        var minor = user("alice@example.com", "p4ssw0rd", 14, "PL", address, Optional.empty());
        var minorWithGuardian =
                user("alice@example.com", "p4ssw0rd", 14, "PL", address, Optional.of("mum@example.com"));

        assertAll(
                () -> assertEquals(List.of(), USER_RULES.check(adult)),
                () -> assertTrue(paths(USER_RULES.check(minor)).contains("guardianEmail")),
                () -> assertEquals(List.of(), USER_RULES.check(minorWithGuardian)));
    }

    @Test
    @DisplayName("inSet reports a null value instead of throwing")
    void inSetIsNullSafe() {
        Rule<String> country = inSet(Set.of("PL", "DE"));

        assertAll(
                () -> assertEquals(List.of(), country.check("PL")),
                () -> assertEquals(1, assertDoesNotThrow(() -> country.check(null)).size()),
                () -> assertEquals(1, country.check("XX").size()));
    }

    @Test
    @DisplayName("every primitive rule tolerates a null value")
    void primitivesAreNullSafe() {
        assertAll(
                () -> assertEquals(1, notBlank().check(null).size()),
                () -> assertEquals(1, minLength(3).check(null).size()),
                () -> assertEquals(1, matches(Pattern.compile("a+")).check(null).size()),
                () -> assertEquals(1, between(0, 10).check(null).size()));
    }

    @Test
    @DisplayName("rules are values: the same rule composes into different validators")
    void rulesAreReusableValues() {
        Rule<String> nonEmptyText = notBlank();

        Rule<Address> cityOnly = on("city", Address::city, nonEmptyText);
        Rule<Address> both = all(cityOnly, on("zipCode", Address::zipCode, nonEmptyText));

        assertAll(
                () -> assertEquals(List.of("city"), paths(cityOnly.check(new Address("", "")))),
                () -> assertEquals(List.of("city", "zipCode"), paths(both.check(new Address("", "")))));
    }

    @Test
    @DisplayName("all() with no rules is the identity of composition")
    void emptyCompositionReportsNothing() {
        assertEquals(List.of(), Mod005CombinatorDsl.<Address>all().check(new Address("", "")));
    }
}
