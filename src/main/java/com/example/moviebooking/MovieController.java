package com.example.moviebooking;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import jakarta.servlet.http.HttpSession;
import java.sql.SQLException;
import java.util.*;

@RestController
@RequestMapping("/api")
public class MovieController {
    private final Database db;
    public MovieController(Database db) { this.db=db; }

    @GetMapping("/movies")
    public List<Map<String,Object>> movies(@RequestParam(defaultValue="") String search) throws SQLException {
        return db.movies(search);
    }

    @PostMapping("/movies")
    public ResponseEntity<?> addMovie(@RequestBody MovieRequest r,HttpSession session) throws SQLException {
        ResponseEntity<?> denied=adminOnly(session); if(denied!=null) return denied;
        ResponseEntity<?> invalid=validateMovie(r); if(invalid!=null) return invalid;
        int id=db.addMovie(r.title().trim(),r.genre().trim(),r.duration(),r.price());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id",id,"message","Movie added."));
    }

    @PutMapping("/movies/{id}")
    public ResponseEntity<?> updateMovie(@PathVariable int id,@RequestBody MovieRequest r,HttpSession session) throws SQLException {
        ResponseEntity<?> denied=adminOnly(session); if(denied!=null) return denied;
        ResponseEntity<?> invalid=validateMovie(r); if(invalid!=null) return invalid;
        if(!db.updateMovie(id,r.title().trim(),r.genre().trim(),r.duration(),r.price()))
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error","Movie not found."));
        return ResponseEntity.ok(Map.of("message","Movie updated."));
    }

    @DeleteMapping("/movies/{id}")
    public ResponseEntity<?> deleteMovie(@PathVariable int id,HttpSession session) throws SQLException {
        ResponseEntity<?> denied=adminOnly(session); if(denied!=null) return denied;
        try {
            if(db.deleteMovie(id)) return ResponseEntity.ok(Map.of("message","Movie deleted."));
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error","Movie not found."));
        } catch(SQLException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error","This movie may have bookings. Keep it rather than deleting its history."));
        }
    }

    @GetMapping("/seats")
    public Map<String,Object> seats(@RequestParam int movieId,
                                    @RequestParam String showTime,
                                    @RequestParam(defaultValue="PVR Lulu, Kochi") String theatre) throws SQLException {
        return Map.of("bookedSeats",db.bookedSeats(movieId,showTime,theatre));
    }

    @GetMapping("/bookings")
    public ResponseEntity<?> bookings(HttpSession session) throws SQLException {
        if(session.getAttribute("userId")==null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error","Please sign in to view bookings."));
        int uid=(Integer)session.getAttribute("userId");
        boolean admin="ADMIN".equals(session.getAttribute("role"));
        return ResponseEntity.ok(db.bookings(uid,admin));
    }

    @PostMapping("/bookings")
    public ResponseEntity<?> book(@RequestBody BookingRequest r,HttpSession session) throws SQLException {
        if(session.getAttribute("userId")==null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error","Please sign in before booking."));
        if(r.movieId()<=0 || r.showTime()==null || r.showTime().isBlank() ||
           r.theatre()==null || r.theatre().isBlank() ||
           r.seat()==null || !r.seat().matches("[A-D][1-8]"))
            return ResponseEntity.badRequest().body(Map.of("error","Choose a movie, theatre, show time and valid seat (A1–D8)."));
        try {
            int id=db.createBooking((Integer)session.getAttribute("userId"),r.movieId(),r.showTime(),r.theatre(),r.seat());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id",id,"message","Ticket booked."));
        } catch(SQLException e) {
            if(e.getMessage()!=null && e.getMessage().toLowerCase().contains("unique"))
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error","That seat is already booked for this show."));
            throw e;
        }
    }

    @DeleteMapping("/bookings/{id}")
    public ResponseEntity<?> cancel(@PathVariable int id,HttpSession session) throws SQLException {
        if(session.getAttribute("userId")==null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error","Please sign in."));
        boolean admin="ADMIN".equals(session.getAttribute("role"));
        if(db.cancelBooking(id,(Integer)session.getAttribute("userId"),admin))
            return ResponseEntity.ok(Map.of("message","Booking cancelled."));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error","Active booking not found or you do not have permission to cancel it."));
    }

    private ResponseEntity<?> adminOnly(HttpSession s) {
        if(s.getAttribute("userId")==null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error","Please sign in."));
        if(!"ADMIN".equals(s.getAttribute("role")))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error","Admin access required."));
        return null;
    }
    private ResponseEntity<?> validateMovie(MovieRequest r) {
        if(r.title()==null || r.title().isBlank() || r.genre()==null || r.genre().isBlank() || r.duration()<=0 || r.price()<0)
            return ResponseEntity.badRequest().body(Map.of("error","Enter a title, genre, positive duration and non-negative price."));
        return null;
    }
    public record MovieRequest(String title,String genre,int duration,double price) {}
    public record BookingRequest(int movieId,String showTime,String theatre,String seat) {}
}
