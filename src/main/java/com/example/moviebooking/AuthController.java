package com.example.moviebooking;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import jakarta.servlet.http.HttpSession;
import java.sql.SQLException;
import java.util.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final Database db;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthController(Database db) { this.db=db; }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest r) throws SQLException {
        if (r.name()==null || r.name().isBlank() || r.email()==null ||
            !r.email().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$") ||
            r.password()==null || r.password().length()<8) {
            return ResponseEntity.badRequest().body(Map.of("error","Enter your name, a valid email, and a password of at least 8 characters."));
        }
        if (db.findUserByEmail(r.email().trim())!=null)
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error","An account with that email already exists."));
        try {
            int id=db.registerUser(r.name().trim(),r.email().trim(),encoder.encode(r.password()));
            HttpSession sessionHolder=null;
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message","Account created. Please sign in."));
        } catch (SQLException e) {
            if (e.getMessage()!=null && e.getMessage().toLowerCase().contains("unique"))
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error","An account with that email already exists."));
            throw e;
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest r, HttpSession session) throws SQLException {
        if (r.email()==null || r.password()==null)
            return ResponseEntity.badRequest().body(Map.of("error","Enter email and password."));
        Map<String,Object> user=db.findUserByEmail(r.email().trim());
        if (user==null || !encoder.matches(r.password(),(String)user.get("passwordHash")))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error","Incorrect email or password."));
        session.setAttribute("userId",user.get("id"));
        session.setAttribute("name",user.get("name"));
        session.setAttribute("email",user.get("email"));
        session.setAttribute("role",user.get("role"));
        return ResponseEntity.ok(publicUser(user));
    }

    @PostMapping("/logout")
    public Map<String,String> logout(HttpSession session) {
        session.invalidate(); return Map.of("message","Signed out.");
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpSession session) {
        if (session.getAttribute("userId")==null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error","Please sign in."));
        return ResponseEntity.ok(Map.of("id",session.getAttribute("userId"),"name",session.getAttribute("name"),
            "email",session.getAttribute("email"),"role",session.getAttribute("role")));
    }

    private Map<String,Object> publicUser(Map<String,Object> u) {
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("id",u.get("id")); out.put("name",u.get("name")); out.put("email",u.get("email")); out.put("role",u.get("role"));
        return out;
    }
    public record RegisterRequest(String name,String email,String password) {}
    public record LoginRequest(String email,String password) {}
}
