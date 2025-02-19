/*
 *
 *  * Copyright The Athenz Authors
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *  
 */

package com.yahoo.athenz.zms;

import com.yahoo.athenz.common.server.db.PoolableDataSource;
import com.yahoo.athenz.common.server.notification.Notification;
import com.yahoo.athenz.zms.notification.GroupMemberExpiryNotificationTask;
import com.yahoo.athenz.zms.notification.RoleMemberExpiryNotificationTask;
import com.yahoo.rdl.Timestamp;
import org.testng.annotations.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.*;

public class ZMSNotificationsTest {

    private final ZMSTestInitializer zmsTestInitializer = new ZMSTestInitializer();

    @BeforeClass
    public void startMemoryMySQL() {
        zmsTestInitializer.startMemoryMySQL();
    }

    @AfterClass
    public void stopMemoryMySQL() {
        zmsTestInitializer.stopMemoryMySQL();
    }

    @BeforeMethod
    public void setUp() throws Exception {
        zmsTestInitializer.setUp();
    }

    @AfterMethod
    public void clearConnections() {
        zmsTestInitializer.clearConnections();
    }

    @Test
    public void testRoleExpiryNotification() {
        TopLevelDomain dom1 = zmsTestInitializer.createTopLevelDomainObject("test-domain1",
                "Test Domain1", "testOrg", zmsTestInitializer.getAdminUser());
        zmsTestInitializer.getZms().postTopLevelDomain(zmsTestInitializer.getMockDomRsrcCtx(), zmsTestInitializer.getAuditRef(), dom1);

        long currentTimeMillis = System.currentTimeMillis();
        Timestamp currentTimestamp = Timestamp.fromMillis(currentTimeMillis);

        // Create 29 members which will be expired in the next 29 days. Each day exactly one member will be expired.
        List<RoleMember> roleMembers = new ArrayList<>();
        for (int i = 0; i <= 28; ++i) {
            Timestamp timestamp = Timestamp.fromMillis(currentTimeMillis + TimeUnit.MILLISECONDS.convert(i, TimeUnit.DAYS));
            roleMembers.add(new RoleMember().setMemberName("user.expireddays" + i).setExpiration(timestamp));
        }

        Role role1 = zmsTestInitializer.createRoleObject("test-domain1", "Role1",
                null, roleMembers);

        zmsTestInitializer.getZms().putRole(zmsTestInitializer.getMockDomRsrcCtx(), "test-domain1", "Role1", zmsTestInitializer.getAuditRef(), role1);
        RoleMemberExpiryNotificationTask roleMemberExpiryNotificationTask = new RoleMemberExpiryNotificationTask(zmsTestInitializer.getZms().dbService, zmsTestInitializer.getZms().userDomainPrefix, zmsTestInitializer.getZms().notificationToEmailConverterCommon);
        List<Notification> notifications = roleMemberExpiryNotificationTask.getNotifications();

        // Email notifications should be sent every 7 days while metrics should be recorded every day
        Set<String> emailNotificationMembers = new HashSet<>(Arrays.asList(new String[]{
                "user.expireddays0",
                "user.expireddays1",
                "user.expireddays7",
                "user.expireddays14",
                "user.expireddays21",
                "user.expireddays28"}));
        for (Notification notification : notifications) {
            String recipient = notification.getRecipients().stream().findFirst().get();
            if (recipient.equals("user.testadminuser")) {
                verifyAdminNotifications(emailNotificationMembers, notification);
            } else {
                if (emailNotificationMembers.contains(recipient)) {
                    assertNotNull(notification.getNotificationAsEmail());
                } else {
                    assertNull(notification.getNotificationAsEmail());
                }
                assertNotNull(notification.getNotificationAsMetrics(currentTimestamp));
            }
        }

        zmsTestInitializer.getZms().deleteTopLevelDomain(zmsTestInitializer.getMockDomRsrcCtx(), "test-domain1", zmsTestInitializer.getAuditRef());
    }

    private void verifyAdminNotifications(Set<String> emailNotificationMembers, Notification notification) {
        String membersList = notification.getDetails().get("membersList");
        if (notification.getNotificationAsEmail() != null) {
            // Email and metric notification for admin
            for (String member : emailNotificationMembers) {
                assertTrue(membersList.contains(member + ";"));
            }
        } else {
            // Metric only notification for admin
            for (String member : emailNotificationMembers) {
                assertFalse(membersList.contains(member + ";"));
            }
        }
    }

    @Test
    public void testGroupExpiryNotification() {
        TopLevelDomain dom1 = zmsTestInitializer.createTopLevelDomainObject("test-domain1",
                "Test Domain1", "testOrg", zmsTestInitializer.getAdminUser());
        zmsTestInitializer.getZms().postTopLevelDomain(zmsTestInitializer.getMockDomRsrcCtx(), zmsTestInitializer.getAuditRef(), dom1);

        long currentTimeMillis = System.currentTimeMillis();
        Timestamp currentTimestamp = Timestamp.fromMillis(currentTimeMillis);

        // Create 29 group members which will be expired in the next 29 days. Each day exactly one member will be expired.
        List<GroupMember> groupMembers = new ArrayList<>();
        for (int i = 0; i <= 28; ++i) {
            Timestamp timestamp = Timestamp.fromMillis(currentTimeMillis + TimeUnit.MILLISECONDS.convert(i, TimeUnit.DAYS));
            groupMembers.add(new GroupMember().setMemberName("user.expireddays" + i).setExpiration(timestamp));
        }

        Group group1 = zmsTestInitializer.createGroupObject("test-domain1", "Group1", groupMembers);

        zmsTestInitializer.getZms().putGroup(zmsTestInitializer.getMockDomRsrcCtx(), "test-domain1", "Group1", zmsTestInitializer.getAuditRef(), group1);
        GroupMemberExpiryNotificationTask groupMemberExpiryNotificationTask = new GroupMemberExpiryNotificationTask(zmsTestInitializer.getZms().dbService, zmsTestInitializer.getZms().userDomainPrefix, zmsTestInitializer.getZms().notificationToEmailConverterCommon);
        List<Notification> notifications = groupMemberExpiryNotificationTask.getNotifications();

        // Email notifications should be sent every 7 days
        Set<String> emailNotificationMembers = new HashSet<>(Arrays.asList(new String[]{
                "user.expireddays0",
                "user.expireddays1",
                "user.expireddays7",
                "user.expireddays14",
                "user.expireddays21",
                "user.expireddays28"}));
        assertEquals(notifications.size(), 7);
        for (Notification notification : notifications) {
            String recipient = notification.getRecipients().stream().findFirst().get();
            if (recipient.equals("user.testadminuser")) {
                verifyAdminNotifications(emailNotificationMembers, notification);
            } else {
                if (emailNotificationMembers.contains(recipient)) {
                    assertNotNull(notification.getNotificationAsEmail());
                } else {
                    assertNull(notification.getNotificationAsEmail());
                }
                assertNotNull(notification.getNotificationAsMetrics(currentTimestamp));
            }
        }

        zmsTestInitializer.getZms().deleteTopLevelDomain(zmsTestInitializer.getMockDomRsrcCtx(), "test-domain1", zmsTestInitializer.getAuditRef());
    }

    @Test
    public void testDisableUserGroupExpiryNotification() {
        TopLevelDomain dom1 = zmsTestInitializer.createTopLevelDomainObject("test-domain1",
                "Test Domain1", "testOrg", zmsTestInitializer.getAdminUser());
        zmsTestInitializer.getZms().postTopLevelDomain(zmsTestInitializer.getMockDomRsrcCtx(), zmsTestInitializer.getAuditRef(), dom1);

        long currentTimeMillis = System.currentTimeMillis();

        // Create 29 group members which will be expired in the next 29 days. Each day exactly one member will be expired.
        List<GroupMember> groupMembers = new ArrayList<>();
        for (int i = 0; i <= 28; ++i) {
            Timestamp timestamp = Timestamp.fromMillis(currentTimeMillis + TimeUnit.MILLISECONDS.convert(i, TimeUnit.DAYS));
            groupMembers.add(new GroupMember().setMemberName("user.expireddays" + i).setExpiration(timestamp));
        }

        Group group1 = zmsTestInitializer.createGroupObject("test-domain1", "Group1", groupMembers);
        // Now disable notification for users
        Map<String, TagValueList> disableUserTags = new HashMap<>();
        disableUserTags.put("zms.DisableReminderNotifications", new TagValueList().setList(Arrays.asList("1")));
        group1.setTags(disableUserTags);

        zmsTestInitializer.getZms().putGroup(zmsTestInitializer.getMockDomRsrcCtx(), "test-domain1", "Group1", zmsTestInitializer.getAuditRef(), group1);
        GroupMemberExpiryNotificationTask groupMemberExpiryNotificationTask = new GroupMemberExpiryNotificationTask(zmsTestInitializer.getZms().dbService, zmsTestInitializer.getZms().userDomainPrefix, zmsTestInitializer.getZms().notificationToEmailConverterCommon);
        List<Notification> notifications = groupMemberExpiryNotificationTask.getNotifications();

        // Email notifications should be sent every 7 days
        Set<String> emailNotificationMembers = new HashSet<>(Arrays.asList(new String[]{
                "user.expireddays0",
                "user.expireddays1",
                "user.expireddays7",
                "user.expireddays14",
                "user.expireddays21",
                "user.expireddays28"}));
        assertEquals(notifications.size(), 1);
        Notification notification = notifications.get(0);
        assertEquals(notification.getRecipients().size(), 1);
        String recipient = notification.getRecipients().stream().findFirst().get();
        assertEquals(recipient, "user.testadminuser");
        verifyAdminNotifications(emailNotificationMembers, notification);

        zmsTestInitializer.getZms().deleteTopLevelDomain(zmsTestInitializer.getMockDomRsrcCtx(), "test-domain1", zmsTestInitializer.getAuditRef());
    }

    @Test
    public void testDisableAdminGroupExpiryNotification() {
        TopLevelDomain dom1 = zmsTestInitializer.createTopLevelDomainObject("test-domain1",
                "Test Domain1", "testOrg", zmsTestInitializer.getAdminUser());
        zmsTestInitializer.getZms().postTopLevelDomain(zmsTestInitializer.getMockDomRsrcCtx(), zmsTestInitializer.getAuditRef(), dom1);

        long currentTimeMillis = System.currentTimeMillis();

        // Create 29 group members which will be expired in the next 29 days. Each day exactly one member will be expired.
        List<GroupMember> groupMembers = new ArrayList<>();
        for (int i = 0; i <= 28; ++i) {
            Timestamp timestamp = Timestamp.fromMillis(currentTimeMillis + TimeUnit.MILLISECONDS.convert(i, TimeUnit.DAYS));
            groupMembers.add(new GroupMember().setMemberName("user.expireddays" + i).setExpiration(timestamp));
        }

        Group group1 = zmsTestInitializer.createGroupObject("test-domain1", "Group1", groupMembers);
        // Now disable notification for admins
        Map<String, TagValueList> disableUserTags = new HashMap<>();
        disableUserTags.put("zms.DisableReminderNotifications", new TagValueList().setList(Arrays.asList("2")));
        group1.setTags(disableUserTags);

        zmsTestInitializer.getZms().putGroup(zmsTestInitializer.getMockDomRsrcCtx(), "test-domain1", "Group1", zmsTestInitializer.getAuditRef(), group1);
        GroupMemberExpiryNotificationTask groupMemberExpiryNotificationTask = new GroupMemberExpiryNotificationTask(zmsTestInitializer.getZms().dbService, zmsTestInitializer.getZms().userDomainPrefix, zmsTestInitializer.getZms().notificationToEmailConverterCommon);
        List<Notification> notifications = groupMemberExpiryNotificationTask.getNotifications();

        // Email notifications should be sent every 7 days
        Set<String> emailNotificationMembers = new HashSet<>(Arrays.asList(new String[]{
                "user.expireddays0",
                "user.expireddays1",
                "user.expireddays7",
                "user.expireddays14",
                "user.expireddays21",
                "user.expireddays28"}));
        assertEquals(notifications.size(), 6);
        for (Notification notification : notifications) {
            assertEquals(notification.getRecipients().size(), 1);
            String recipient = notification.getRecipients().stream().findFirst().get();
            assertTrue(emailNotificationMembers.contains(recipient));
            emailNotificationMembers.remove(recipient);
        }
        assertTrue(emailNotificationMembers.isEmpty());

        zmsTestInitializer.getZms().deleteTopLevelDomain(zmsTestInitializer.getMockDomRsrcCtx(), "test-domain1", zmsTestInitializer.getAuditRef());
    }

    @Test
    public void testDisableAllGroupExpiryNotification() {
        TopLevelDomain dom1 = zmsTestInitializer.createTopLevelDomainObject("test-domain1",
                "Test Domain1", "testOrg", zmsTestInitializer.getAdminUser());
        zmsTestInitializer.getZms().postTopLevelDomain(zmsTestInitializer.getMockDomRsrcCtx(), zmsTestInitializer.getAuditRef(), dom1);

        long currentTimeMillis = System.currentTimeMillis();

        // Create 29 group members which will be expired in the next 29 days. Each day exactly one member will be expired.
        List<GroupMember> groupMembers = new ArrayList<>();
        for (int i = 0; i <= 28; ++i) {
            Timestamp timestamp = Timestamp.fromMillis(currentTimeMillis + TimeUnit.MILLISECONDS.convert(i, TimeUnit.DAYS));
            groupMembers.add(new GroupMember().setMemberName("user.expireddays" + i).setExpiration(timestamp));
        }

        Group group1 = zmsTestInitializer.createGroupObject("test-domain1", "Group1", groupMembers);
        // Now disable all notifications
        Map<String, TagValueList> disableUserTags = new HashMap<>();
        disableUserTags.put("zms.DisableReminderNotifications", new TagValueList().setList(Arrays.asList("3")));
        group1.setTags(disableUserTags);

        zmsTestInitializer.getZms().putGroup(zmsTestInitializer.getMockDomRsrcCtx(), "test-domain1", "Group1", zmsTestInitializer.getAuditRef(), group1);
        GroupMemberExpiryNotificationTask groupMemberExpiryNotificationTask = new GroupMemberExpiryNotificationTask(zmsTestInitializer.getZms().dbService, zmsTestInitializer.getZms().userDomainPrefix, zmsTestInitializer.getZms().notificationToEmailConverterCommon);
        List<Notification> notifications = groupMemberExpiryNotificationTask.getNotifications();
        assertEquals(notifications.size(), 0);
        zmsTestInitializer.getZms().deleteTopLevelDomain(zmsTestInitializer.getMockDomRsrcCtx(), "test-domain1", zmsTestInitializer.getAuditRef());
    }
}
