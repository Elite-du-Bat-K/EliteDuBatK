package fr.umontpellier.iut.discordbot.services;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import fr.umontpellier.iut.discordbot.database.dataobjects.ChannelLock;
import fr.umontpellier.iut.discordbot.database.repositories.ChannelLockRepository;
import fr.umontpellier.iut.discordbot.services.exceptions.ChannelLockStateServiceException;
import fr.umontpellier.iut.discordbot.services.exceptions.ServiceException;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ChannelLockService {
    private final Logger logger;
    private final Gson gson;
    private final ChannelLockRepository repository;

    public ChannelLockService(ChannelLockRepository repository) {
        this.logger = LoggerFactory.getLogger(this.getClass());
        this.gson = new Gson();
        this.repository = repository;
    }

    public Optional<ChannelLock> getChannelLock(int id) {
        return repository.findByPrimaryKey(id);
    }

    public Optional<ChannelLock> getChannelLockByChannelId(String channelId) {
        try {
            return repository.findByChannelId(channelId);
        } catch (Exception e) {
            throw new ServiceException("Impossible de recuperer l'etat du verrouillage.", e);
        }
    }

    public boolean isChannelLocked(String channelId) {
        return getChannelLockByChannelId(channelId).isPresent();
    }

    public void lockChannel(TextChannel channel, Guild guild, Member member) {
        String channelId = channel.getId();

        if (isChannelLocked(channelId)) {
            throw new ChannelLockStateServiceException("Ce salon est deja verrouille.");
        }

        try {
            String channelStateJson = captureChannelState(channel);
            lockChannelPermissions(channel);

            ChannelLock lock = new ChannelLock(
                    0,
                    channelId,
                    guild.getId(),
                    member.getId(),
                    System.currentTimeMillis(),
                    channelStateJson
            );
            repository.insert(lock);

            logger.info("Channel {} locked by user {}", channelId, member.getId());
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("Erreur lors du verrouillage du salon.", e);
        }
    }

    public void unlockChannel(TextChannel channel) {
        String channelId = channel.getId();
        ChannelLock lock = getChannelLockByChannelId(channelId)
                .orElseThrow(() -> new ChannelLockStateServiceException("Ce salon n'est pas verrouille."));

        try {
            restoreChannelState(channel, lock.getChannelStateJson());
            repository.deleteByChannelId(channelId);
            logger.info("Channel {} unlocked", channelId);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("Erreur lors du deverrouillage du salon.", e);
        }
    }

    private String captureChannelState(TextChannel channel) {
        Map<String, Map<String, Object>> permissionOverrides = new HashMap<>();

        for (PermissionOverride override : channel.getPermissionOverrides()) {
            Map<String, Object> overrideData = new HashMap<>();
            String type = override.isMemberOverride() ? "MEMBER" : "ROLE";
            overrideData.put("type", type);
            overrideData.put("id", String.valueOf(override.getIdLong()));
            overrideData.put("allowed", String.valueOf(override.getAllowedRaw()));
            overrideData.put("denied", String.valueOf(override.getDeniedRaw()));
            if (override.isRoleOverride() && override.getRole() != null) {
                overrideData.put("name", override.getRole().getName());
            }

            permissionOverrides.put(override.getId(), overrideData);
        }

        Map<String, Object> state = new HashMap<>();
        state.put("permissionOverrides", permissionOverrides);
        return gson.toJson(state);
    }

    private void lockChannelPermissions(TextChannel channel) {
        channel.upsertPermissionOverride(channel.getGuild().getPublicRole())
                .deny(Permission.MESSAGE_SEND, Permission.MESSAGE_ADD_REACTION)
                .complete();
    }

    private void restoreChannelState(TextChannel channel, String channelStateJson) {
        try {
            Map<String, Object> state = gson.fromJson(
                    channelStateJson,
                    new TypeToken<Map<String, Object>>() {}.getType()
            );

            for (PermissionOverride override : channel.getPermissionOverrides()) {
                override.delete().complete();
            }

            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> permissionOverrides =
                    (Map<String, Map<String, Object>>) state.get("permissionOverrides");

            Guild guild = channel.getGuild();
            if (permissionOverrides == null) {
                return;
            }

            for (Map<String, Object> overrideData : permissionOverrides.values()) {
                try {
                    String type = (String) overrideData.get("type");
                    long id = parseLong(overrideData.get("id"));
                    long allowed = parseLong(overrideData.get("allowed"));
                    long denied = parseLong(overrideData.get("denied"));

                    if ("ROLE".equals(type)) {
                        Role role = resolveRole(guild, id, overrideData);
                        if (role != null) {
                            channel.upsertPermissionOverride(role)
                                    .setPermissions(allowed, denied)
                                    .complete();
                        } else {
                            logger.debug("Role {} not found in guild, skipping permission override", id);
                        }
                    } else if ("MEMBER".equals(type)) {
                        Member member = resolveMember(guild, id);
                        if (member != null) {
                            channel.upsertPermissionOverride(member)
                                    .setPermissions(allowed, denied)
                                    .complete();
                        } else {
                            logger.warn("Member {} not found in guild, skipping permission override", id);
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Error restoring permission override: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            throw new ServiceException("Impossible de restaurer l'etat du salon.", e);
        }
    }

    private long parseLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String stringValue) {
            return Long.parseLong(stringValue);
        }
        throw new IllegalArgumentException("Unsupported numeric value: " + value);
    }

    private Role resolveRole(Guild guild, long roleId, Map<String, Object> overrideData) {
        if (roleId == guild.getIdLong()) {
            return guild.getPublicRole();
        }

        Role role = guild.getRoleById(roleId);
        if (role != null) {
            return role;
        }

        Object roleNameObj = overrideData.get("name");
        if (roleNameObj instanceof String roleName && !roleName.isBlank()) {
            var matches = guild.getRolesByName(roleName, false);
            if (!matches.isEmpty()) {
                return matches.getFirst();
            }
        }

        return null;
    }

    private Member resolveMember(Guild guild, long memberId) {
        Member member = guild.getMemberById(memberId);
        if (member != null) {
            return member;
        }

        try {
            return guild.retrieveMemberById(memberId).complete();
        } catch (Exception e) {
            return null;
        }
    }
}
