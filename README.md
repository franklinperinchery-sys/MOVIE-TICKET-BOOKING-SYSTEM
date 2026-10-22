# CineBook Advanced — learning starter

This version extends the basic demo with:
- User registration and sign-in
- BCrypt password hashing
- Session-based sign-in state
- USER and ADMIN roles
- Users see only their own booking history and can cancel only their own bookings
- Admins can see all bookings, add movies and delete movies (deletion blocked by existing booking history)
- Movie search with local poster artwork, seat selection, theatre selection, unique active seat booking per movie/show/theatre (cancelled seats can be booked again), cancellation
- SQLite database created automatically

## Requirements
- Java 17+
- Maven 3.8+

Check:
```bash
java -version
mvn -version
```

## Run locally
1. Extract the ZIP.
2. Open a terminal in the folder containing `pom.xml`.
3. Run:
   ```bash
   mvn spring-boot:run
   ```
4. Visit `http://localhost:8080`.

The SQLite file `moviebooking.db` is created in the working directory. The app inserts sample movies on first run. Movie cards use local SVG poster-style artwork in `src/main/resources/static/posters`, so the images work without an external image service. These are original demo designs, not official studio posters.

## How to create an administrator
For safety, public registration always creates a USER. To grant ADMIN for a local classroom demo:
1. Stop the app.
2. Open `moviebooking.db` with a SQLite database browser.
3. Run this SQL, replacing the email with the account you registered:
   ```sql
   UPDATE users SET role = 'ADMIN' WHERE email = 'your-email@example.com';
   ```
4. Restart the app, sign out and sign in again.

Do not expose the demo publicly before adding production security controls.

## Suggested code reading order
1. `MovieBookingApplication.java` — application start
2. `Database.java` — schema, prepared statements, CRUD
3. `AuthController.java` — registration, password hashing, sessions
4. `MovieController.java` — movie and booking APIs, role/ownership checks
5. `static/index.html` — page structure
6. `static/styles.css` — responsive styling
7. `static/app.js` — browser events and API calls

## Important limitations
This is an educational prototype, not a production-ready ticketing platform. It does not implement payment processing, email verification, password reset, rate limiting, audit logging, robust CSRF protection, or a hardened deployment setup. It uses simple session-based authentication in controller code to make the flow understandable for beginners. Before public deployment, have the security architecture reviewed, add CSRF protection and rate limiting, use HTTPS, secure cookies, secrets management, database backups/migrations, and proper logging. Never use real customer data in a classroom demo.

## Existing database warning
This version expects the `bookings` table to have a `user_id` column and the `users` table. If you are upgrading from the earlier starter ZIP, back up and rename or remove the old `moviebooking.db` for a fresh demo database. Do not do this if you need to preserve existing bookings.

## Theatre catalogue note

The theatre dropdown contains example venues in Kerala, including Kochi, Thiruvananthapuram, Kottayam, Changanassery, Thrissur, Kozhikode, Kollam and Alappuzha. This is a classroom demo list, not a live showtime feed. Verify venue names and current screenings before treating the data as real booking information. Seat availability is tracked separately for each movie, showtime and theatre.

## Movie catalogue note

The starter database includes sample titles across Malayalam, Tamil, Hindi, Telugu, and English. These are demonstration catalogue entries for the college project; the app does not fetch live cinema listings or verify whether a title is currently screening at a particular theatre. Release dates and theatrical availability can change, so update the sample catalogue before a real deployment.
