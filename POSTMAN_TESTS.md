# Ay-Khedma Backend - Postman Testing Guide

**Base URL**: `http://localhost:8080`

---

## 🔐 1. Authentication Endpoints (`/auth`)

### 1.1 Register (POST /auth/register)
**Method**: POST  
**URL**: `http://localhost:8080/auth/register`  
**Content-Type**: `application/json`

#### Test Case 1: Register Consumer (Success)
```json
{
  "name": "John Doe",
  "email": "john@example.com",
  "password": "Password123",
  "phoneNumber": "01012345678",
  "userType": "CONSUMER"
}
```
**Expected Response**: 200 OK
```json
{
  "message": "Registered successfully"
}
```

#### Test Case 2: Register Provider (Requires Additional Fields)
```json
{
  "name": "Ahmed Electrician",
  "email": "ahmed@providers.com",
  "password": "Password123",
  "phoneNumber": "01098765432",
  "userType": "PROVIDER",
  "serviceType": "ELECTRICAL",
  "city": "Cairo",
  "serviceArea": "Zamalek"
}
```
**Expected Response**: 200 OK

#### Test Case 3: Invalid Email
```json
{
  "name": "Test User",
  "email": "invalid-email",
  "password": "Password123",
  "phoneNumber": "01012345678",
  "userType": "CONSUMER"
}
```
**Expected Response**: 400 Bad Request

#### Test Case 4: Short Password
```json
{
  "name": "Test User",
  "email": "test@example.com",
  "password": "123",
  "phoneNumber": "01012345678",
  "userType": "CONSUMER"
}
```
**Expected Response**: 400 Bad Request

#### Test Case 5: Invalid Phone Number
```json
{
  "name": "Test User",
  "email": "test@example.com",
  "password": "Password123",
  "phoneNumber": "123456",
  "userType": "CONSUMER"
}
```
**Expected Response**: 400 Bad Request

---

### 1.2 Login (POST /auth/login)
**Method**: POST  
**URL**: `http://localhost:8080/auth/login`  
**Content-Type**: `application/json`

#### Test Case 1: Valid Login with Email
```json
{
  "emailOrPhone": "john@example.com",
  "password": "Password123"
}
```
**Expected Response**: 200 OK
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "email": "john@example.com"
}
```

#### Test Case 2: Valid Login with Phone Number
```json
{
  "emailOrPhone": "01012345678",
  "password": "Password123"
}
```
**Expected Response**: 200 OK

#### Test Case 3: Wrong Password
```json
{
  "emailOrPhone": "john@example.com",
  "password": "WrongPassword1"
}
```
**Expected Response**: 401 Unauthorized

#### Test Case 4: Non-existent User
```json
{
  "emailOrPhone": "nobody@example.com",
  "password": "Password123"
}
```
**Expected Response**: 404 Not Found

#### Test Case 5: Blank Fields
```json
{
  "emailOrPhone": "",
  "password": ""
}
```
**Expected Response**: 400 Bad Request

---

## 👥 2. Consumer Endpoints (`/api/v1/consumers`)

**Required Header for authenticated requests**:
```
Authorization: Bearer {token}
```

### 2.1 Get Consumer Profile
**Method**: GET  
**URL**: `http://localhost:8080/api/v1/consumers/{consumerId}`  
**Authorization**: Admin or Provider role

**Example**: `http://localhost:8080/api/v1/consumers/2`

**Expected Response**: 200 OK
```json
{
  "id": 2,
  "name": "John Doe",
  "email": "john@example.com",
  "phoneNumber": "01012345678",
  "profilePicture": "https://...",
  "savedProviders": []
}
```

### 2.2 Get My Consumer Profile
**Method**: GET  
**URL**: `http://localhost:8080/api/v1/consumers/me`  
**Authorization**: Consumer role required

**Expected Response**: 200 OK (Same as above)

### 2.3 Update My Consumer Profile
**Method**: PUT  
**URL**: `http://localhost:8080/api/v1/consumers/me`  
**Content-Type**: `application/json`  
**Authorization**: Consumer role required

```json
{
  "name": "John Updated Doe",
  "phoneNumber": "01987654321"
}
```
**Expected Response**: 200 OK

### 2.4 Update My Profile Picture
**Method**: POST  
**URL**: `http://localhost:8080/api/v1/consumers/me/profile-picture`  
**Content-Type**: `multipart/form-data`  
**Authorization**: Consumer role required  
**Max File Size**: 50MB (JPEG, PNG, GIF)

**Form Data**:
- Key: `file`
- Value: [Binary file data]

**Expected Response**: 200 OK

### 2.5 Delete My Profile Picture
**Method**: DELETE  
**URL**: `http://localhost:8080/api/v1/consumers/me/profile-picture`  
**Authorization**: Consumer role required

**Expected Response**: 204 No Content

### 2.6 Save Provider to Favorites
**Method**: POST  
**URL**: `http://localhost:8080/api/v1/consumers/me/saved-providers/{providerId}`  
**Authorization**: Consumer role required

**Example**: `http://localhost:8080/api/v1/consumers/me/saved-providers/1`

**Expected Response**: 201 Created
```json
{
  "success": true,
  "message": "Provider saved successfully"
}
```

### 2.7 Remove Saved Provider
**Method**: DELETE  
**URL**: `http://localhost:8080/api/v1/consumers/me/saved-providers/{providerId}`  
**Authorization**: Consumer role required

**Example**: `http://localhost:8080/api/v1/consumers/me/saved-providers/1`

**Expected Response**: 200 OK

### 2.8 Get My Saved/Favorite Providers
**Method**: GET  
**URL**: `http://localhost:8080/api/v1/consumers/me/saved-providers`  
**Authorization**: Consumer role required

**Expected Response**: 200 OK
```json
[
  {
    "id": 1,
    "name": "Ahmed Electrician",
    "serviceType": "ELECTRICAL",
    "rating": 4.5,
    "reviewCount": 25
  }
]
```

---

## 🔧 3. Provider Endpoints (`/api/v1/providers`)

### 3.1 Get Provider Profile (Public)
**Method**: GET  
**URL**: `http://localhost:8080/api/v1/providers/{providerId}`  
**Authorization**: Consumer or Admin role required

**Example**: `http://localhost:8080/api/v1/providers/1`

**Expected Response**: 200 OK
```json
{
  "id": 1,
  "name": "Ahmed Electrician",
  "email": "ahmed@example.com",
  "phoneNumber": "01098765432",
  "serviceType": "ELECTRICAL",
  "description": "Professional electrician with 10 years experience",
  "rating": 4.5,
  "reviewCount": 25,
  "verified": true,
  "verificationStatus": "VERIFIED",
  "profilePicture": "https://..."
}
```

### 3.2 Get My Provider Profile
**Method**: GET  
**URL**: `http://localhost:8080/api/v1/providers/me`  
**Authorization**: Provider role required

**Expected Response**: 200 OK (Same format as above)

### 3.3 Update My Provider Profile
**Method**: PUT  
**URL**: `http://localhost:8080/api/v1/providers/me`  
**Content-Type**: `application/json`  
**Authorization**: Provider role required

```json
{
  "description": "Professional electrician with 15 years experience",
  "serviceType": "ELECTRICAL_AND_PLUMBING",
  "phoneNumber": "01987654321"
}
```
**Expected Response**: 200 OK

### 3.4 Update Provider Profile Picture
**Method**: POST  
**URL**: `http://localhost:8080/api/v1/providers/me/profile-picture`  
**Content-Type**: `multipart/form-data`  
**Authorization**: Provider role required  
**Max File Size**: 5MB (JPEG, PNG, GIF)

**Form Data**:
- Key: `file`
- Value: [Binary file data]

**Expected Response**: 200 OK

### 3.5 Get Provider Schedule
**Method**: GET  
**URL**: `http://localhost:8080/api/v1/providers/{providerId}/schedule`  
**Authorization**: None required (Public endpoint)

**Example**: `http://localhost:8080/api/v1/providers/1/schedule`

**Expected Response**: 200 OK
```json
{
  "providerId": 1,
  "workingDays": [
    {
      "id": 1,
      "dayOfWeek": "MONDAY",
      "startTime": "08:00",
      "endTime": "18:00"
    },
    {
      "id": 2,
      "dayOfWeek": "TUESDAY",
      "startTime": "08:00",
      "endTime": "18:00"
    }
  ],
  "specialDays": []
}
```

### 3.6 Add Working Day Template
**Method**: POST  
**URL**: `http://localhost:8080/api/v1/providers/me/schedule/working-days`  
**Content-Type**: `application/json`  
**Authorization**: Provider role required

```json
{
  "dayOfWeek": "MONDAY",
  "startTime": "08:00",
  "endTime": "18:00"
}
```
**Expected Response**: 201 Created
```json
{
  "providerId": 1,
  "workingDays": [
    {
      "id": 1,
      "dayOfWeek": "MONDAY",
      "startTime": "08:00",
      "endTime": "18:00"
    }
  ]
}
```

### 3.7 Update Working Day Template
**Method**: PUT  
**URL**: `http://localhost:8080/api/v1/providers/me/schedule/working-days/{workingDayId}`  
**Content-Type**: `application/json`  
**Authorization**: Provider role required

```json
{
  "dayOfWeek": "MONDAY",
  "startTime": "09:00",
  "endTime": "19:00"
}
```
**Expected Response**: 200 OK

---

## 📅 4. Booking Endpoints (`/api/bookings`)

### 4.1 Request Booking (Consumer)
**Method**: POST  
**URL**: `http://localhost:8080/api/bookings/request-booking`  
**Content-Type**: `application/json`  
**Authorization**: Consumer role required

```json
{
  "providerId": 1,
  "requestedDate": "2026-04-15",
  "requestedStartTime": "10:00",
  "problemDescription": "My electrical outlets are not working"
}
```
**Expected Response**: 201 Created
```json
{
  "id": 10,
  "consumer": {
    "id": 2,
    "name": "John Doe"
  },
  "provider": {
    "id": 1,
    "name": "Ahmed Electrician"
  },
  "requestedDate": "2026-04-15",
  "requestedStartTime": "10:00",
  "estimatedDuration": null,
  "problemDescription": "My electrical outlets are not working",
  "status": "PENDING"
}
```

### 4.2 Accept Booking (Provider)
**Method**: POST  
**URL**: `http://localhost:8080/api/bookings/accept-booking`  
**Content-Type**: `application/json`  
**Authorization**: Provider role required

```json
{
  "bookingId": 10,
  "estimatedDuration": 120
}
```
**Expected Response**: 200 OK (Success)
```json
{
  "status": "ACCEPTED",
  "booking": {
    "id": 10,
    "consumer": {
      "id": 2,
      "name": "John Doe"
    },
    "provider": {
      "id": 1,
      "name": "Ahmed Electrician"
    },
    "requestedDate": "2026-04-15",
    "requestedStartTime": "10:00",
    "estimatedDuration": 120,
    "problemDescription": "My electrical outlets are not working",
    "status": "ACCEPTED"
  }
}
```

**Expected Response**: 409 Conflict (Scheduling conflict)
```json
{
  "status": "CONFLICT",
  "conflictingBookings": [
    {
      "id": 8,
      "requestedDate": "2026-04-15",
      "requestedStartTime": "09:00",
      "estimatedDuration": 90
    }
  ]
}
```

**Expected Response**: 200 OK with Warning (End time exceeds working hours)
```json
{
  "status": "WARNING",
  "warningMessage": "The booking end time will exceed the end time of the working day"
}
```

### 4.3 Decline Booking (Provider)
**Method**: POST  
**URL**: `http://localhost:8080/api/bookings/decline-booking/{bookingId}`  
**Authorization**: Provider role required

**Example**: `http://localhost:8080/api/bookings/decline-booking/10`

**Expected Response**: 200 OK
```json
{
  "id": 10,
  "status": "DECLINED"
}
```

### 4.4 Cancel Booking (Consumer or Provider)
**Method**: POST  
**URL**: `http://localhost:8080/api/bookings/cancel-booking`  
**Content-Type**: `application/json`  
**Authorization**: Consumer or Provider role required

```json
{
  "bookingId": 10,
  "cancellationReason": "Provider is not available"
}
```
**Expected Response**: 200 OK
```json
{
  "id": 10,
  "status": "CANCELLED"
}
```

### 4.5 Get Bookings by Status
**Method**: GET  
**URL**: `http://localhost:8080/api/bookings/get-bookings`  
**Authorization**: Consumer or Provider role required

**Query Parameters**:
- `status` (optional): `PENDING`, `ACCEPTED`, `COMPLETED`, `CANCELLED`, `DECLINED`
- `page` (optional): Page number (default: 0)
- `size` (optional): Page size (default: 20)
- `sort` (optional): Sort field (e.g., `requestedDate,desc`)

**Example**: 
```
http://localhost:8080/api/bookings/get-bookings?status=PENDING&page=0&size=10
```

**Expected Response**: 200 OK
```json
{
  "content": [
    {
      "id": 10,
      "consumer": {
        "id": 2,
        "name": "John Doe"
      },
      "provider": {
        "id": 1,
        "name": "Ahmed Electrician"
      },
      "requestedDate": "2026-04-15",
      "requestedStartTime": "10:00",
      "estimatedDuration": 120,
      "problemDescription": "My electrical outlets are not working",
      "status": "PENDING"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 10,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

### 4.6 Get Upcoming Bookings for Today
**Method**: GET  
**URL**: `http://localhost:8080/api/bookings/upcoming-bookings`  
**Authorization**: Consumer or Provider role required

**Expected Response**: 200 OK
```json
[
  {
    "id": 10,
    "consumer": {
      "id": 2,
      "name": "John Doe"
    },
    "provider": {
      "id": 1,
      "name": "Ahmed Electrician"
    },
    "requestedDate": "2026-04-13",
    "requestedStartTime": "14:00",
    "estimatedDuration": 120,
    "status": "ACCEPTED"
  }
]
```

---

## 🔔 5. Notification Endpoints (`/api/v1/notifications`)

### 5.1 Get User Notifications (Paginated)
**Method**: GET  
**URL**: `http://localhost:8080/api/v1/notifications`  
**Authorization**: Not required (User ID via header or parameter)

**Header OR Query Parameter**:
```
Header: X-User-Id: 1
OR
Query Param: userId=1
```

**Query Parameters**:
- `page`: Page number (default: 0)
- `size`: Page size (default: 20)
- `sort`: Sort field (e.g., `createdAt,desc`)

**Example**:
```
http://localhost:8080/api/v1/notifications?userId=1&page=0&size=20
```

**Expected Response**: 200 OK
```json
{
  "content": [
    {
      "id": 100,
      "userId": 1,
      "type": "BOOKING_CONFIRMATION",
      "title": "Booking Confirmed",
      "content": "Your booking with Ahmed Electrician is confirmed",
      "isRead": false,
      "createdAt": "2026-04-13T10:30:00"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

### 5.2 Get Unread Notification Count
**Method**: GET  
**URL**: `http://localhost:8080/api/v1/notifications/unread/count`  
**Authorization**: Not required

**Header**: 
```
X-User-Id: 1
```

**Expected Response**: 200 OK
```json
{
  "count": 5
}
```

### 5.3 Get Single Notification
**Method**: GET  
**URL**: `http://localhost:8080/api/v1/notifications/{notificationId}`  
**Authorization**: Not required

**Example**: `http://localhost:8080/api/v1/notifications/100`  
**Header**: `X-User-Id: 1`

**Expected Response**: 200 OK
```json
{
  "id": 100,
  "userId": 1,
  "type": "BOOKING_CONFIRMATION",
  "title": "Booking Confirmed",
  "content": "Your booking with Ahmed Electrician is confirmed",
  "isRead": false,
  "createdAt": "2026-04-13T10:30:00"
}
```

### 5.4 Mark Single Notification as Read
**Method**: PUT  
**URL**: `http://localhost:8080/api/v1/notifications/{notificationId}/read`  
**Authorization**: Not required

**Example**: `http://localhost:8080/api/v1/notifications/100/read`  
**Header**: `X-User-Id: 1`

**Expected Response**: 200 OK
```json
{
  "success": true,
  "message": "Notification marked as read"
}
```

### 5.5 Mark Multiple Notifications as Read
**Method**: PUT  
**URL**: `http://localhost:8080/api/v1/notifications/read-batch`  
**Content-Type**: `application/json`  
**Authorization**: Not required

**Header**: `X-User-Id: 1`

```json
{
  "notificationIds": [100, 101, 102]
}
```

**Expected Response**: 200 OK
```json
{
  "success": true,
  "message": "Notifications marked as read"
}
```

### 5.6 Mark All Notifications as Read
**Method**: PUT  
**URL**: `http://localhost:8080/api/v1/notifications/read-all`  
**Authorization**: Not required

**Header**: `X-User-Id: 1`

**Expected Response**: 200 OK
```json
{
  "success": true,
  "message": "All notifications marked as read"
}
```

### 5.7 Delete Notification
**Method**: DELETE  
**URL**: `http://localhost:8080/api/v1/notifications/{notificationId}`  
**Authorization**: Not required

**Example**: `http://localhost:8080/api/v1/notifications/100`  
**Header**: `X-User-Id: 1`

**Expected Response**: 200 OK
```json
{
  "success": true,
  "message": "Notification deleted"
}
```

### 5.8 Send Test Notification
**Method**: POST  
**URL**: `http://localhost:8080/api/v1/notifications/test/send`  
**Content-Type**: `application/json`

```json
{
  "userId": 1,
  "title": "Test Notification",
  "content": "This is a test notification",
  "type": "BOOKING_CONFIRMATION",
  "sendInApp": true,
  "sendPush": true
}
```

**Expected Response**: 200 OK
```json
{
  "success": true,
  "message": "Test notification sent successfully"
}
```

### 5.9 Send Email Notification
**Method**: POST  
**URL**: `http://localhost:8080/api/v1/notifications/send-email`  
**Content-Type**: `application/json`

**Query Parameter**: `email=test@example.com`

```json
{
  "userId": 1,
  "title": "Email Notification",
  "content": "This is an email notification",
  "type": "BOOKING_CONFIRMATION"
}
```

**Expected Response**: 200 OK
```json
{
  "success": true,
  "message": "Email notification sent successfully",
  "email": "test@example.com",
  "userId": 1,
  "type": "BOOKING_CONFIRMATION"
}
```

### 5.10 Send Push Notification
**Method**: POST  
**URL**: `http://localhost:8080/api/v1/notifications/send-push`  
**Content-Type**: `application/json`

**Query Parameter**: `userId=1`

```json
{
  "userId": 1,
  "title": "Push Notification",
  "content": "This is a push notification",
  "type": "BOOKING_CONFIRMATION"
}
```

**Expected Response**: 200 OK
```json
{
  "success": true,
  "message": "Push notification sent successfully",
  "userId": 1,
  "type": "BOOKING_CONFIRMATION"
}
```

### 5.11 Send In-App Notification
**Method**: POST  
**URL**: `http://localhost:8080/api/v1/notifications/send-inapp`  
**Content-Type**: `application/json`

**Query Parameter**: `userId=1`

```json
{
  "userId": 1,
  "title": "In-App Notification",
  "content": "This is an in-app notification",
  "type": "BOOKING_CONFIRMATION"
}
```

**Expected Response**: 200 OK
```json
{
  "success": true,
  "message": "In-app notification sent successfully",
  "userId": 1,
  "type": "BOOKING_CONFIRMATION"
}
```

### 5.12 Filter Notifications by Date Range
**Method**: GET  
**URL**: `http://localhost:8080/api/v1/notifications/filter`  
**Authorization**: Not required

**Header**: `X-User-Id: 1`

**Query Parameters**:
- `startDate`: Start date (ISO format, e.g., `2026-04-01T00:00:00`)
- `endDate`: End date (ISO format, e.g., `2026-04-13T23:59:59`)
- `page`: Page number (default: 0)
- `size`: Page size (default: 20)

**Example**:
```
http://localhost:8080/api/v1/notifications/filter?startDate=2026-04-01T00:00:00&endDate=2026-04-13T23:59:59&page=0&size=20
```

**Expected Response**: 200 OK
```json
{
  "content": [
    {
      "id": 100,
      "userId": 1,
      "type": "BOOKING_CONFIRMATION",
      "title": "Booking Confirmed",
      "content": "Your booking is confirmed",
      "isRead": false,
      "createdAt": "2026-04-13T10:30:00"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

---

## 📍 6. Location Endpoints (`/api/v1/locations`)

### 6.1 Save Consumer Location
**Method**: POST  
**URL**: `http://localhost:8080/api/v1/locations/consumer/me`  
**Content-Type**: `application/json`  
**Authorization**: Consumer role required

```json
{
  "latitude": 30.0444,
  "longitude": 31.2357,
  "address": "123 Main St, Cairo",
  "city": "Cairo",
  "postalCode": "11511"
}
```

**Expected Response**: 201 Created
```json
{
  "id": 1,
  "latitude": 30.0444,
  "longitude": 31.2357,
  "address": "123 Main St, Cairo",
  "city": "Cairo",
  "postalCode": "11511"
}
```

### 6.2 Update Consumer Location
**Method**: PUT  
**URL**: `http://localhost:8080/api/v1/locations/consumer/me`  
**Content-Type**: `application/json`  
**Authorization**: Consumer role required

```json
{
  "latitude": 30.0555,
  "longitude": 31.2500,
  "address": "456 New St, Cairo",
  "city": "Cairo",
  "postalCode": "11512"
}
```

**Expected Response**: 200 OK
```json
{
  "id": 1,
  "latitude": 30.0555,
  "longitude": 31.2500,
  "address": "456 New St, Cairo",
  "city": "Cairo",
  "postalCode": "11512"
}
```

### 6.3 Patch Consumer Location
**Method**: PATCH  
**URL**: `http://localhost:8080/api/v1/locations/consumers/me`  
**Content-Type**: `application/json`  
**Authorization**: Consumer role required

```json
{
  "address": "789 Updated St, Cairo"
}
```

**Expected Response**: 200 OK

### 6.4 Get My Consumer Location
**Method**: GET  
**URL**: `http://localhost:8080/api/v1/locations/consumers/me`  
**Authorization**: Consumer role required

**Expected Response**: 200 OK
```json
{
  "latitude": 30.0555,
  "longitude": 31.2500,
  "address": "456 New St, Cairo",
  "city": "Cairo",
  "postalCode": "11512"
}
```

### 6.5 Get Consumer Location (Admin/Provider)
**Method**: GET  
**URL**: `http://localhost:8080/api/v1/locations/consumer/{consumerId}`  
**Authorization**: Admin or Provider role required

**Example**: `http://localhost:8080/api/v1/locations/consumer/2`

**Expected Response**: 200 OK

---

## 🛡️ 7. Protected Endpoints Test

### 7.1 Test Without Token (Should Return 403)
**Method**: GET  
**URL**: `http://localhost:8080/api/users`

**Expected Response**: 403 Forbidden
```json
{
  "error": "Access denied",
  "message": "Unauthorized access"
}
```

---

## 📊 Enum Values Reference

### UserType
- `CONSUMER`
- `PROVIDER`

### ServiceType
- `ELECTRICAL`
- `PLUMBING`
- `CARPENTRY`
- `PAINTING`
- `CLEANING`
- `HVAC`
- `GENERAL_MAINTENANCE`
- `OTHER`

### DayOfWeek
- `MONDAY`
- `TUESDAY`
- `WEDNESDAY`
- `THURSDAY`
- `FRIDAY`
- `SATURDAY`
- `SUNDAY`

### BookingStatus
- `PENDING`
- `ACCEPTED`
- `COMPLETED`
- `CANCELLED`
- `DECLINED`

### NotificationType
- `BOOKING_CONFIRMATION`
- `BOOKING_CANCELLED`
- `PROVIDER_REVIEW`
- `CONSUMER_REVIEW`
- `SYSTEM_ALERT`
- `OTHER`

### VerificationStatus
- `PENDING`
- `VERIFIED`
- `REJECTED`

---

## 🔑 Common Headers

### For Authenticated Requests
```
Authorization: Bearer {token_from_login}
Content-Type: application/json
```

### For Notifications
```
X-User-Id: {userId}
```

---

## 📝 Tips for Testing in Postman

1. **Save Token After Login**: 
   - After login, copy the `token` value
   - Use it in the `Authorization` header as: `Bearer {token}`

2. **Use Environment Variables**:
   - Create a Postman environment variable `{{base_url}}` = `http://localhost:8080`
   - Create `{{token}}` and update it after each login
   - Create `{{userId}}` for user-specific tests

3. **Pre-request Script** (to add Authorization header automatically):
   ```javascript
   if (pm.environment.get("token")) {
       pm.request.headers.add({
           key: 'Authorization',
           value: 'Bearer ' + pm.environment.get("token")
       });
   }
   ```

4. **Test Valid Dates**: Use dates in the future (e.g., `2026-04-15` or later)

5. **Phone Numbers**: Must start with `01` and have 11 digits total (Egyptian format)

---

## ✅ Quick Checklist for Endpoint Testing

- [ ] Register Consumer
- [ ] Register Provider
- [ ] Login with Email
- [ ] Login with Phone
- [ ] Get Consumer Profile
- [ ] Update Consumer Profile
- [ ] Update Consumer Profile Picture
- [ ] Request Booking
- [ ] Accept Booking
- [ ] Decline Booking
- [ ] Cancel Booking
- [ ] Get Bookings by Status
- [ ] Get Upcoming Bookings
- [ ] Get Notifications
- [ ] Mark Notification as Read
- [ ] Send Test Notification
- [ ] Get Provider Profile
- [ ] Update Provider Profile
- [ ] Get Provider Schedule
- [ ] Add Working Day
- [ ] Save Provider to Favorites
- [ ] Get Saved Providers

