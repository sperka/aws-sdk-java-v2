/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.awssdk.profiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.utils.StringInputStream;

/**
 * Validate the functionality of {@link ProfileFile}.
 */
public class ProfileFileTest {
    @Test
    public void emptyFilesHaveNoProfiles() {
        assertThat(configFileProfiles("")).isEmpty();
    }

    @Test
    public void emptyProfilesHaveNoProperties() {
        assertThat(configFileProfiles("[profile foo]"))
            .isEqualTo(profiles(profile("foo")));
    }

    @Test
    public void profileDefinitionsMustEndWithBrackets() {
        assertThatThrownBy(() -> configFileProfiles("[profile foo"))
            .hasMessageContaining("Profile definition must end with ']'");
    }

    @Test
    public void profileNamesShouldBeTrimmed() {
        assertThat(configFileProfiles("[profile \tfoo \t]"))
            .isEqualTo(profiles(profile("foo")));
    }

    @Test
    public void tabsCanSeparateProfileNamesFromProfilePrefix() {
        assertThat(configFileProfiles("[profile\tfoo]"))
            .isEqualTo(profiles(profile("foo")));
    }

    @Test
    public void propertiesMustBeDefinedInAProfile() {
        assertThatThrownBy(() -> configFileProfiles("name = value"))
            .hasMessageContaining("Expected a profile definition");
    }

    @Test
    public void profilesCanContainProperties() {
        assertThat(configFileProfiles("[profile foo]\n" +
                                      "name = value"))
            .isEqualTo(profiles(profile("foo", property("name", "value"))));
    }

    @Test
    public void windowsStyleLineEndingsAreSupported() {
        assertThat(configFileProfiles("[profile foo]\r\n" +
                                      "name = value"))
            .isEqualTo(profiles(profile("foo", property("name", "value"))));
    }

    @Test
    public void equalsSignsAreSupportedInPropertyNames() {
        assertThat(configFileProfiles("[profile foo]\r\n" +
                                      "name = val=ue"))
            .isEqualTo(profiles(profile("foo", property("name", "val=ue"))));
    }

    @Test
    public void unicodeCharactersAreSupportedInPropertyValues() {
        assertThat(configFileProfiles("[profile foo]\r\n" +
                                      "name = \uD83D\uDE02"))
            .isEqualTo(profiles(profile("foo", property("name", "\uD83D\uDE02"))));
    }

    @Test
    public void profilesCanContainMultipleProperties() {
        assertThat(configFileProfiles("[profile foo]\n" +
                                      "name = value\n" +
                                      "name2 = value2"))
            .isEqualTo(profiles(profile("foo",
                                        property("name", "value"),
                                        property("name2", "value2"))));
    }

    @Test
    public void propertyKeysAndValuesAreTrimmed() {
        assertThat(configFileProfiles("[profile foo]\n" +
                                      "name \t=  \tvalue \t"))
            .isEqualTo(profiles(profile("foo", property("name", "value"))));
    }

    @Test
    public void propertyValuesCanBeEmpty() {
        assertThat(configFileProfiles("[profile foo]\n" +
                                      "name ="))
            .isEqualTo(profiles(profile("foo", property("name", ""))));
    }

    @Test
    public void propertyKeysCannotBeEmpty() {
        assertThatThrownBy(() -> configFileProfiles("[profile foo]\n" +
                                                    "= value"))
            .hasMessageContaining("Property did not have a name");
    }

    @Test
    public void propertyDefinitionsMustContainAnEqualsSign() {
        assertThatThrownBy(() -> configFileProfiles("[profile foo]\n" +
                                                    "key : value"))
            .hasMessageContaining("Expected an '=' sign defining a property");
    }

    @Test
    public void multipleProfilesCanBeEmpty() {
        assertThat(configFileProfiles("[profile foo]\n" +
                                      "[profile bar]"))
            .isEqualTo(profiles(profile("foo"),
                                profile("bar")));
    }

    @Test
    public void multipleProfilesCanHaveProperties() {
        assertThat(configFileProfiles("[profile foo]\n" +
                                      "name = value\n" +
                                      "[profile bar]\n" +
                                      "name2 = value2"))
            .isEqualTo(profiles(profile("foo", property("name", "value")),
                                profile("bar", property("name2", "value2"))));
    }

    @Test
    public void blankLinesAreIgnored() {
        assertThat(configFileProfiles("\t \n" +
                                      "[profile foo]\n" +
                                      "\t\n" +
                                      " \n" +
                                      "name = value\n" +
                                      "\t \n" +
                                      "[profile bar]\n" +
                                      " \t"))
            .isEqualTo(profiles(profile("foo", property("name", "value")),
                                profile("bar")));
    }

    @Test
    public void poundSignCommentsAreIgnored() {
        assertThat(configFileProfiles("# Comment\n" +
                                      "[profile foo] # Comment\n" +
                                      "name = value # Comment with # sign"))
            .isEqualTo(profiles(profile("foo", property("name", "value"))));
    }

    @Test
    public void semicolonCommentsAreIgnored() {
        assertThat(configFileProfiles("; Comment\n" +
                                      "[profile foo] ; Comment\n" +
                                      "name = value ; Comment with ; sign"))
            .isEqualTo(profiles(profile("foo", property("name", "value"))));
    }

    @Test
    public void commentTypesCanBeUsedTogether() {
        assertThat(configFileProfiles("# Comment\n" +
                                      "[profile foo] ; Comment\n" +
                                      "name = value # Comment with ; sign"))
            .isEqualTo(profiles(profile("foo", property("name", "value"))));
    }

    @Test
    public void commentsCanBeEmpty() {
        assertThat(configFileProfiles(";\n" +
                                      "[profile foo];\n" +
                                      "name = value ;\n"))
            .isEqualTo(profiles(profile("foo", property("name", "value"))));
    }

    @Test
    public void commentsCanBeAdjacentToProfileNames() {
        assertThat(configFileProfiles("[profile foo]; Adjacent semicolons\n" +
                                      "[profile bar]# Adjacent pound signs"))
            .isEqualTo(profiles(profile("foo"),
                                profile("bar")));
    }

    @Test
    public void commentsAdjacentToValuesAreIncludedInTheValue() {
        assertThat(configFileProfiles("[profile foo]\n" +
                                      "name = value; Adjacent semicolons\n" +
                                      "name2 = value# Adjacent pound signs"))
            .isEqualTo(profiles(profile("foo",
                                        property("name", "value; Adjacent semicolons"),
                                        property("name2", "value# Adjacent pound signs"))));
    }

    @Test
    public void propertyValuesCanBeContinuedOnTheNextLine() {
        assertThat(configFileProfiles("[profile foo]\n" +
                                      "name = value\n" +
                                      " -continued"))
            .isEqualTo(profiles(profile("foo",
                                        property("name", "value\n-continued"))));
    }

    @Test
    public void propertyValuesCanBeContinuedAcrossMultipleLines() {
        assertThat(configFileProfiles("[profile foo]\n" +
                                      "name = value\n" +
                                      " -continued\n" +
                                      " -and-continued"))
            .isEqualTo(profiles(profile("foo",
                                        property("name", "value\n-continued\n-and-continued"))));
    }

    @Test
    public void continuationValuesIncludeSemicolonComments() {
        assertThat(configFileProfiles("[profile foo]\n" +
                                      "name = value\n" +
                                      " -continued ; Comment"))
            .isEqualTo(profiles(profile("foo",
                                        property("name", "value\n-continued ; Comment"))));
    }

    @Test
    public void continuationsCannotBeUsedOutsideOfAProfile() {
        assertThatThrownBy(() -> configFileProfiles(" -continued"))
            .hasMessageContaining("Expected a profile or property definition");
    }

    @Test
    public void continuationsCannotBeUsedOutsideOfAProperty() {
        assertThatThrownBy(() -> configFileProfiles("[profile foo]\n" +
                                                    " -continued"))
            .hasMessageContaining("Expected a profile or property definition");
    }

    @Test
    public void continuationsResetWithProfileDefinition() {
        assertThatThrownBy(() -> configFileProfiles("[profile foo]\n" +
                                                    "name = value\n" +
                                                    "[profile foo]\n" +
                                                    " -continued"))
            .hasMessageContaining("Expected a profile or property definition");
    }

    @Test
    public void duplicateProfilesInTheSameFileMergeProperties() {
        assertThat(configFileProfiles("[profile foo]\n" +
                                      "name = value\n" +
                                      "[profile foo]\n" +
                                      "name2 = value2"))
            .isEqualTo(profiles(profile("foo",
                                        property("name", "value"),
                                        property("name2", "value2"))));
    }

    @Test
    public void duplicatePropertiesInAProfileUseTheLastOneDefined() {
        assertThat(configFileProfiles("[profile foo]\n" +
                                      "name = value\n" +
                                      "name = value2"))
            .isEqualTo(profiles(profile("foo", property("name", "value2"))));
    }

    @Test
    public void duplicatePropertiesInDuplicateProfilesUseTheLastOneDefined() {
        assertThat(configFileProfiles("[profile foo]\n" +
                                      "name = value\n" +
                                      "[profile foo]\n" +
                                      "name = value2"))
            .isEqualTo(profiles(profile("foo", property("name", "value2"))));
    }

    @Test
    public void defaultProfileWithProfilePrefixOverridesDefaultProfileWithoutPrefixWhenPrefixedIsFirst() {
        assertThat(configFileProfiles("[profile default]\n" +
                                      "name = value\n" +
                                      "[default]\n" +
                                      "name2 = value2"))
            .isEqualTo(profiles(profile("default", property("name", "value"))));
    }

    @Test
    public void defaultProfileWithProfilePrefixOverridesDefaultProfileWithoutPrefixWhenPrefixedIsLast() {
        assertThat(configFileProfiles("[default]\n" +
                                      "name2 = value2\n" +
                                      "[profile default]\n" +
                                      "name = value"))
            .isEqualTo(profiles(profile("default", property("name", "value"))));
    }

    @Test
    public void invalidProfilesNamesAreIgnored() {
        assertThat(aggregateFileProfiles("[profile in valid]\n" +
                                         "name = value\n",
                                         "[in valid 2]\n" +
                                         "name2 = value2"))
            .isEqualTo(profiles());
    }

    @Test
    public void invalidPropertyNamesAreIgnored() {
        assertThat(configFileProfiles("[profile foo]\n" +
                                      "in valid = value"))
            .isEqualTo(profiles(profile("foo")));
    }

    @Test
    public void allValidProfileNameCharactersAreSupported() {
        assertThat(configFileProfiles("[profile ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_./%@:+]"))
            .isEqualTo(profiles(profile("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_./%@:+")));
    }

    @Test
    public void allValidPropertyNameCharactersAreSupported() {
        assertThat(configFileProfiles("[profile foo]\nABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_./%@:+ = value"))
            .isEqualTo(profiles(profile("foo", property("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_./%@:+",
                                                        "value"))));
    }

    @Test
    public void propertiesCanHaveSubProperties() {
        assertThat(configFileProfiles("[profile foo]\n" +
                                      "s3 =\n" +
                                      " name = value"))
            .isEqualTo(profiles(profile("foo",
                                        property("s3", "\nname = value"),
                                        property("s3.name", "value"))));
    }

    @Test
    public void invalidSubPropertiesCauseAnError() {
        assertThatThrownBy(() -> configFileProfiles("[profile foo]\n" +
                                                    "s3 =\n" +
                                                    " invalid"))
            .hasMessageContaining("Expected an '=' sign defining a property");
    }

    @Test
    public void subPropertiesCanHaveEmptyValues() {
        assertThat(configFileProfiles("[profile foo]\n" +
                                      "s3 =\n" +
                                      " name ="))
            .isEqualTo(profiles(profile("foo",
                                        property("s3", "\nname ="),
                                        property("s3.name", ""))));
    }

    @Test
    public void invalidSubPropertiesCannotHaveEmptyNames() {
        assertThatThrownBy(() -> configFileProfiles("[profile foo]\n" +
                                                    "s3 =\n" +
                                                    " = value"))
            .hasMessageContaining("Property did not have a name");
    }

    @Test
    public void subPropertiesCanHaveInvalidNames() {
        assertThat(configFileProfiles("[profile foo]\n" +
                                      "s3 =\n" +
                                      " in valid = value"))
            .isEqualTo(profiles(profile("foo", property("s3", "\nin valid = value"))));
    }

    @Test
    public void subPropertiesCanHaveBlankLines() {
        assertThat(configFileProfiles("[profile foo]\n" +
                                      "s3 =\n" +
                                      " name = value\n" +
                                      "\t \n" +
                                      " name2 = value2"))
            .isEqualTo(profiles(profile("foo",
                                        property("s3", "\nname = value\nname2 = value2"),
                                        property("s3.name", "value"),
                                        property("s3.name2", "value2"))));
    }

    @Test
    public void profilesDuplicatedInMultipleFilesAreMerged() {
        assertThat(aggregateFileProfiles("[profile foo]\n" +
                                         "name = value\n",
                                         "[foo]\n" +
                                         "name2 = value2"))
            .isEqualTo(profiles(profile("foo",
                                        property("name", "value"),
                                        property("name2", "value2"))));
    }

    @Test
    public void defaultProfilesWithMixedPrefixesInConfigFileIgnoreOneWithoutPrefixWhenMerging() {
        assertThat(configFileProfiles("[profile default]\n" +
                                      "name = value\n" +
                                      "[default]\n" +
                                      "name2 = value2\n" +
                                      "[profile default]\n" +
                                      "name3 = value3"))
            .isEqualTo(profiles(profile("default",
                                        property("name", "value"),
                                        property("name3", "value3"))));
    }

    @Test
    public void duplicatePropertiesBetweenFilesUsesCredentialsProperty() {
        assertThat(aggregateFileProfiles("[profile foo]\n" +
                                         "name = value",
                                         "[foo]\n" +
                                         "name = value2"))
            .isEqualTo(profiles(profile("foo", property("name", "value2"))));
    }

    @Test
    public void configProfilesWithoutPrefixAreIgnored() {
        assertThat(configFileProfiles("[foo]\n" +
                                      "name = value"))
            .isEqualTo(profiles());
    }

    @Test
    public void credentialsProfilesWithPrefixAreIgnored() {
        assertThat(credentialFileProfiles("[profile foo]\n" +
                                          "name = value"))
            .isEqualTo(profiles());
    }

    @Test
    public void sectionsInCredentialsFilesIsIgnored() {

        String ASSUME_ROLE_PROFILE =
            "[test]\n"
            + "region = us-west-1\n"
            + "credential_source = Environment\n"
            + "sso_session = session\n"
            + "role_arn = some_Arn\n"
            + "[sso-session session]\n"
            + "sso_region = us-west-1\n"
            + "start_url = someUrl\n";

        ProfileFile profileFile = credentialFile(ASSUME_ROLE_PROFILE);
        Map<String, Profile> profileMap = profileFile.profiles();
        assertThat(profileMap)
            .isEqualTo(profiles(profile("test", property("region", "us-west-1")
                , property("credential_source", "Environment")
                , property("sso_session", "session"),
                                        property("role_arn", "some_Arn"))));

        ;
        assertThat(profileFile.getSection("sso-session", "session")).isNotPresent();
    }

    @Test
    public void invalidSectionNamesAreNotAddedInSectionListOfProfileFiles() {

        ProfileFile aggregatedProfileFiles = ProfileFile.aggregator()
                                                        .addFile(credentialFile("[in valid 2]\n"
                                                                                + "name2 = value2"))
                                                        .addFile(configFile("[profile validSection]\n"
                                                                            + "sso_session = validSso\n"
                                                                            + "[sso-session validSso]\n"
                                                                            + "start_url = Valid-url\n"))
                                                        .addFile(configFile("[profile section]\n"
                                                                            + "sso_session = sso invalid\n"
                                                                            + "[sso-session sso invalid]\n"
                                                                            + "start_url = url\n"))
                                                        .build();

        assertThat(aggregatedProfileFiles.profiles())
            .isEqualTo(profiles(profile("section", property("sso_session", "sso invalid")),
                       profile("validSection", property("sso_session", "validSso"))));

        assertThat(aggregatedProfileFiles.getSection("sso-session", "sso")).isNotPresent();
        assertThat(aggregatedProfileFiles.getSection("sso-session", "sso invalid")).isNotPresent();
        assertThat(aggregatedProfileFiles.getSection("sso-session", "validSso").get())
            .isEqualTo(profile("validSso", property("start_url", "Valid-url")));
    }

    @Test
    public void defaultSessionNameIsNotSupported_when_session_doesNot_exist() {

        String nonExistentSessionName = "nonExistentSession";
        ProfileFile aggregatedProfileFiles = ProfileFile.aggregator()
                                                        .addFile(configFile("[profile validSection]\n"
                                                                            + "sso_session = " + nonExistentSessionName + "\n"
                                                                            + "[profile " + nonExistentSessionName + "]\n"
                                                                            + "start_url = Valid-url-2\n"
                                                                            + "[sso-session default]\n"
                                                                            + "start_url = Valid-url\n"))
                                                        .build();

        assertThat(aggregatedProfileFiles.profiles())
            .isEqualTo(profiles(profile("validSection", property("sso_session",  nonExistentSessionName)),
                                profile(nonExistentSessionName, property("start_url", "Valid-url-2"))));

        assertThat(aggregatedProfileFiles.getSection("sso-session", nonExistentSessionName)).isNotPresent();
    }

    @Test
    public void sessionIsNotAdjacentToProfile() {

        ProfileFile aggregatedProfileFiles = ProfileFile.aggregator()
                                                        .addFile(configFile("[profile profile1]\n"
                                                                            + "sso_session = sso-token1\n"
                                                                            + "[profile profile2]\n"
                                                                            + "region  = us-west-2\n"
                                                                            + "[default]\n"
                                                                            + "default_property = property1\n"))
                                                        .addFile(configFile("[sso-session sso-token1]\n"
                                                                            + "start_url = startUrl1\n"
                                                                            + "[profile profile3]\n"
                                                                            + "region = us-east-1\n")
                                                        ).build();

        assertThat(aggregatedProfileFiles.profiles())
            .isEqualTo(profiles(profile("profile1", property("sso_session",  "sso-token1")),
                                profile("default", property("default_property", "property1")),
                                profile("profile2", property("region", "us-west-2")),
                                profile("profile3", property("region", "us-east-1"))

            ));

        assertThat(aggregatedProfileFiles.getSection("sso-session", "sso-token1")).isPresent();
        assertThat(aggregatedProfileFiles.getSection("sso-session", "sso-token1").get()).isEqualTo(
            profile("sso-token1", property("start_url", "startUrl1"))         );
    }

    @Test
    public void exceptionIsThrown_when_sectionDoesnotHaveASquareBracket() {


        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(
            () -> ProfileFile.aggregator()
                             .addFile(configFile("[profile profile1]\n"
                                                 + "sso_session = sso-token1\n"
                                                 + "[sso-session sso-token1\n"
                                                 + "start_url = startUrl1\n")
                             ).build()).withMessageContaining("Section definition must end with ']' on line 3");

    }

    @Test
    public void loadingDefaultProfileFileWorks() {
        ProfileFile.defaultProfileFile();
    }

    @Test
    public void returnsEmptyMap_when_AwsFilesDoNotExist() {
        ProfileFile missingProfile = ProfileFile.aggregator()
                                                .build();

        assertThat(missingProfile.profiles()).isEmpty();
        assertThat(missingProfile.profiles()).isInstanceOf(Map.class);
    }

    @Test
    public void builderValidatesContentRequired() {
        assertThatThrownBy(() -> ProfileFile.builder().type(ProfileFile.Type.CONFIGURATION).build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("content or contentLocation must be set.");

    }

    private ProfileFile configFile(String configFile) {
        return ProfileFile.builder()
                          .content(configFile)
                          .type(ProfileFile.Type.CONFIGURATION)
                          .build();
    }

    private Map<String, Profile> configFileProfiles(String configFile) {
        return configFile(configFile).profiles();
    }

    private ProfileFile credentialFile(String credentialFile) {
        return ProfileFile.builder()
                          .content(credentialFile)
                          .type(ProfileFile.Type.CREDENTIALS)
                          .build();
    }

    private Map<String, Profile> credentialFileProfiles(String credentialFile) {
        return credentialFile(credentialFile).profiles();
    }

    private Map<String, Profile> aggregateFileProfiles(String configFile, String credentialFile) {
        return ProfileFile.aggregator()
                          .addFile(credentialFile(credentialFile))
                          .addFile(configFile(configFile))
                          .build()
                          .profiles();
    }

    private Map<String, Profile> profiles(Profile... profiles) {
        Map<String, Profile> result = new HashMap<>();
        Stream.of(profiles).forEach(p -> result.put(p.name(), p));
        return result;
    }

    @SafeVarargs
    private final Profile profile(String name, Map.Entry<String, String>... properties) {
        Map<String, String> propertiesMap = new HashMap<>();
        Stream.of(properties).forEach(p -> propertiesMap.put(p.getKey(), p.getValue()));
        return Profile.builder().name(name).properties(propertiesMap).build();
    }

    private Map.Entry<String, String> property(String name, String value) {
        return new AbstractMap.SimpleImmutableEntry<>(name, value);
    }
}
