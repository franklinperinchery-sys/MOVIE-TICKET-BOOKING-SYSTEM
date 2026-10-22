package com.example.moviebooking;

import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import java.sql.*;
import java.util.*;

@Component
public class Database {
    private static final String URL = "jdbc:sqlite:moviebooking.db";

    @PostConstruct
    public void initialize() {
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.executeUpdate("PRAGMA foreign_keys = ON");
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS users (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  name TEXT NOT NULL,
                  email TEXT NOT NULL UNIQUE,
                  password_hash TEXT NOT NULL,
                  role TEXT NOT NULL DEFAULT 'USER',
                  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS movies (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  title TEXT NOT NULL,
                  genre TEXT NOT NULL,
                  duration INTEGER NOT NULL,
                  price REAL NOT NULL
                )
                """);
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS bookings (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  user_id INTEGER NOT NULL,
                  movie_id INTEGER NOT NULL,
                  show_time TEXT NOT NULL,
                  theatre TEXT NOT NULL DEFAULT 'PVR Lulu, Kochi',
                  seat TEXT NOT NULL,
                  status TEXT NOT NULL DEFAULT 'BOOKED',
                  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  FOREIGN KEY(user_id) REFERENCES users(id),
                  FOREIGN KEY(movie_id) REFERENCES movies(id)
                )
                """);
            // Add the theatre column to databases created by an earlier version of CineBook.
            boolean hasTheatreColumn = false;
            try (ResultSet columns = s.executeQuery("PRAGMA table_info(bookings)")) {
                while (columns.next()) {
                    if ("theatre".equalsIgnoreCase(columns.getString("name"))) {
                        hasTheatreColumn = true;
                        break;
                    }
                }
            }
            if (!hasTheatreColumn) {
                s.executeUpdate("ALTER TABLE bookings ADD COLUMN theatre TEXT NOT NULL DEFAULT 'PVR Lulu, Kochi'");
            }

            s.executeUpdate("DROP INDEX IF EXISTS uq_active_seat");
            s.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS uq_active_seat ON bookings(movie_id, show_time, theatre, seat) WHERE status='BOOKED'");
            // Add sample movies individually so new titles also appear in an existing database.
            Object[][] sampleMovies = {
                {"Udumbanchola Vision", "Malayalam · Action, Drama", 135, 180.0},
                {"Chiram", "Malayalam · Drama, Romance", 116, 170.0},
                {"Randu Rahasyangal", "Malayalam · Crime, Thriller", 121, 180.0},
                {"Poru: Oru Tharam, Randu Tharam", "Malayalam · Comedy, Drama", 139, 170.0},
                {"Dhoomakethu", "Malayalam · Drama", 128, 180.0},
                {"Aaram", "Malayalam · Thriller", 132, 180.0},
                {"Lokah Chapter 1: Chandra", "Malayalam · Fantasy, Action", 151, 220.0},
                {"L2: Empuraan", "Malayalam · Action, Thriller", 179, 250.0},
                {"Thudarum", "Malayalam · Drama, Thriller", 163, 220.0},
                {"Jailer 2", "Tamil · Action, Drama", 163, 230.0},
                {"Drones", "Tamil · Thriller", 128, 180.0},
                {"Titanic", "Tamil · Drama, Romance", 135, 180.0},
                {"Dorothy", "Tamil · Drama", 142, 190.0},
                {"Good Bad Ugly", "Tamil · Action, Comedy", 138, 220.0},
                {"Dragon", "Tamil · Comedy, Drama", 157, 200.0},
                {"Nayyi Navelli", "Hindi · Drama", 130, 180.0},
                {"Prahaar", "Hindi · Action, Drama", 140, 190.0},
                {"Pehla Pyaar Doosri Baar", "Hindi · Romance, Comedy · Upcoming", 125, 180.0},
                {"Dhurandhar", "Hindi · Action, Thriller", 155, 230.0},
                {"Saiyaara", "Hindi · Romance, Drama", 156, 200.0},
                {"Ranabaali", "Telugu · Action, Drama", 170, 220.0},
                {"Garividi Lakshmi", "Telugu · Drama, Biography", 122, 180.0},
                {"Crazy Kalyanam", "Telugu · Comedy, Drama", 144, 190.0},
                {"VISA - Vintara Saradaga", "Telugu · Comedy, Drama", 132, 180.0},
                {"Aadarsha Kutumbam House No. 47", "Telugu · Family, Drama · Upcoming", 142, 200.0},
                {"Interstellar", "English · Sci-Fi", 169, 250.0},
                {"Inception", "English · Thriller", 148, 220.0},
                {"The Dark Knight", "English · Action", 152, 240.0},
                {"Inside Out 2", "English · Animation", 96, 180.0}
            };

            String existsSql = "SELECT 1 FROM movies WHERE lower(title) = lower(?) LIMIT 1";
            String insertSql = "INSERT INTO movies(title,genre,duration,price) VALUES(?,?,?,?)";
            try (PreparedStatement exists = c.prepareStatement(existsSql);
                 PreparedStatement insert = c.prepareStatement(insertSql)) {
                for (Object[] movie : sampleMovies) {
                    exists.setString(1, (String) movie[0]);
                    try (ResultSet rs = exists.executeQuery()) {
                        if (rs.next()) continue;
                    }
                    insert.setString(1, (String) movie[0]);
                    insert.setString(2, (String) movie[1]);
                    insert.setInt(3, (Integer) movie[2]);
                    insert.setDouble(4, (Double) movie[3]);
                    insert.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not initialize SQLite database", e);
        }
    }

    public Connection connect() throws SQLException {
        Connection c = DriverManager.getConnection(URL);
        try (Statement s = c.createStatement()) { s.execute("PRAGMA foreign_keys = ON"); }
        return c;
    }

    public Map<String,Object> findUserByEmail(String email) throws SQLException {
        try (Connection c=connect(); PreparedStatement p=c.prepareStatement(
                "SELECT id,name,email,password_hash,role FROM users WHERE lower(email)=lower(?)")) {
            p.setString(1,email);
            try (ResultSet rs=p.executeQuery()) {
                if (!rs.next()) return null;
                Map<String,Object> u=new LinkedHashMap<>();
                u.put("id",rs.getInt("id")); u.put("name",rs.getString("name"));
                u.put("email",rs.getString("email")); u.put("passwordHash",rs.getString("password_hash"));
                u.put("role",rs.getString("role")); return u;
            }
        }
    }

    public int registerUser(String name,String email,String hash) throws SQLException {
        try (Connection c=connect(); PreparedStatement p=c.prepareStatement(
                "INSERT INTO users(name,email,password_hash,role) VALUES(?,?,?,'USER')")) {
            p.setString(1,name); p.setString(2,email.toLowerCase(Locale.ROOT)); p.setString(3,hash);
            p.executeUpdate();
            try (Statement s=c.createStatement(); ResultSet rs=s.executeQuery("SELECT last_insert_rowid()")) {
                return rs.next()?rs.getInt(1):-1;
            }
        }
    }

    public List<Map<String,Object>> movies(String search) throws SQLException {
        List<Map<String,Object>> rows=new ArrayList<>();
        try (Connection c=connect(); PreparedStatement p=c.prepareStatement(
                "SELECT id,title,genre,duration,price FROM movies WHERE lower(title) LIKE lower(?) ORDER BY title")) {
            p.setString(1,"%"+(search==null?"":search.trim())+"%");
            try (ResultSet rs=p.executeQuery()) {
                while(rs.next()) {
                    Map<String,Object> r=new LinkedHashMap<>();
                    r.put("id",rs.getInt("id")); r.put("title",rs.getString("title"));
                    r.put("genre",rs.getString("genre")); r.put("duration",rs.getInt("duration"));
                    r.put("price",rs.getDouble("price")); rows.add(r);
                }
            }
        }
        return rows;
    }

    public int addMovie(String title,String genre,int duration,double price) throws SQLException {
        try (Connection c=connect(); PreparedStatement p=c.prepareStatement(
                "INSERT INTO movies(title,genre,duration,price) VALUES(?,?,?,?)")) {
            p.setString(1,title); p.setString(2,genre); p.setInt(3,duration); p.setDouble(4,price); p.executeUpdate();
            try (Statement s=c.createStatement(); ResultSet rs=s.executeQuery("SELECT last_insert_rowid()")) {
                return rs.next()?rs.getInt(1):-1;
            }
        }
    }

    public boolean updateMovie(int id,String title,String genre,int duration,double price) throws SQLException {
        try (Connection c=connect(); PreparedStatement p=c.prepareStatement(
                "UPDATE movies SET title=?,genre=?,duration=?,price=? WHERE id=?")) {
            p.setString(1,title); p.setString(2,genre); p.setInt(3,duration); p.setDouble(4,price); p.setInt(5,id);
            return p.executeUpdate()>0;
        }
    }

    public boolean deleteMovie(int id) throws SQLException {
        try (Connection c=connect(); PreparedStatement p=c.prepareStatement("DELETE FROM movies WHERE id=?")) {
            p.setInt(1,id); return p.executeUpdate()>0;
        }
    }

    public List<Map<String,Object>> bookings(Integer userId, boolean admin) throws SQLException {
        List<Map<String,Object>> rows=new ArrayList<>();
        String sql="""
          SELECT b.id,b.user_id,u.name customer_name,u.email,m.title movie_title,b.movie_id,
                 b.show_time,b.theatre,b.seat,b.status,b.created_at
          FROM bookings b JOIN users u ON u.id=b.user_id JOIN movies m ON m.id=b.movie_id
          """+(admin?"":" WHERE b.user_id=?")+" ORDER BY b.id DESC";
        try (Connection c=connect(); PreparedStatement p=c.prepareStatement(sql)) {
            if (!admin) p.setInt(1,userId);
            try (ResultSet rs=p.executeQuery()) {
                while(rs.next()) {
                    Map<String,Object> r=new LinkedHashMap<>();
                    r.put("id",rs.getInt("id")); r.put("userId",rs.getInt("user_id"));
                    r.put("customerName",rs.getString("customer_name")); r.put("email",rs.getString("email"));
                    r.put("movieTitle",rs.getString("movie_title")); r.put("movieId",rs.getInt("movie_id"));
                    r.put("showTime",rs.getString("show_time")); r.put("theatre",rs.getString("theatre"));
                    r.put("seat",rs.getString("seat"));
                    r.put("status",rs.getString("status")); r.put("createdAt",rs.getString("created_at")); rows.add(r);
                }
            }
        }
        return rows;
    }

    public List<String> bookedSeats(int movieId,String showTime,String theatre) throws SQLException {
        List<String> seats=new ArrayList<>();
        try (Connection c=connect(); PreparedStatement p=c.prepareStatement(
                "SELECT seat FROM bookings WHERE movie_id=? AND show_time=? AND theatre=? AND status='BOOKED'")) {
            p.setInt(1,movieId); p.setString(2,showTime); p.setString(3,theatre);
            try (ResultSet rs=p.executeQuery()) { while(rs.next()) seats.add(rs.getString(1)); }
        }
        return seats;
    }

    public int createBooking(int userId,int movieId,String showTime,String theatre,String seat) throws SQLException {
        try (Connection c=connect(); PreparedStatement p=c.prepareStatement(
                "INSERT INTO bookings(user_id,movie_id,show_time,theatre,seat) VALUES(?,?,?,?,?)")) {
            p.setInt(1,userId); p.setInt(2,movieId); p.setString(3,showTime);
            p.setString(4,theatre); p.setString(5,seat); p.executeUpdate();
            try (Statement s=c.createStatement(); ResultSet rs=s.executeQuery("SELECT last_insert_rowid()")) {
                return rs.next()?rs.getInt(1):-1;
            }
        }
    }

    public boolean cancelBooking(int id,int userId,boolean admin) throws SQLException {
        String sql="UPDATE bookings SET status='CANCELLED' WHERE id=? AND status='BOOKED'"+(admin?"":" AND user_id=?");
        try (Connection c=connect(); PreparedStatement p=c.prepareStatement(sql)) {
            p.setInt(1,id); if(!admin) p.setInt(2,userId); return p.executeUpdate()>0;
        }
    }
}
