package com.aykhedma.mapper;

import com.aykhedma.dto.request.RegisterRequest;
import com.aykhedma.dto.response.UserResponse;
import com.aykhedma.model.user.Consumer;
import com.aykhedma.model.user.Provider;
import com.aykhedma.model.user.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserMapper {
    @Mapping(source = "role", target = "role")
    UserResponse toUserResponse(User user);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "enabled", constant = "true")
    @Mapping(target = "role", source = "userType")
    @Mapping(target = "averageRating", ignore = true)
    @Mapping(target = "bookings", ignore = true)
    @Mapping(target = "savedProviders", ignore = true)
    @Mapping(target = "totalBookings", constant = "0")
    Consumer toConsumer(RegisterRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "enabled", constant = "true")
    @Mapping(target = "role", source = "userType")
    @Mapping(target = "verificationStatus", constant = "PENDING")
    @Mapping(target = "completedJobs", constant = "0")
    @Mapping(target = "averageRating", constant = "0.0")
    @Mapping(target = "bookingRate", constant = "0")
    @Mapping(target = "acceptanceRate", constant = "100")
    @Mapping(target = "emergencyEnabled", constant = "false")
    @Mapping(target = "bio", source = "bio")
    @Mapping(target = "price", source = "price")
    @Mapping(target = "nationalId", source = "nationalId")
    @Mapping(target = "documents", ignore = true)
    @Mapping(target = "location", ignore = true)
    @Mapping(target = "schedule", ignore = true)
    @Mapping(target = "bookings", ignore = true)
    Provider toProvider(RegisterRequest request);

    List<UserResponse> toUserResponseList(List<User> users);
}
