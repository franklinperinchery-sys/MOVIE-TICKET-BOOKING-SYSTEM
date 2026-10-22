const $ = (id) => document.getElementById(id);

let movies = [];
let currentUser = null;
let selectedSeat = null;

const seatNames = Array.from({ length: 32 }, (_, index) => {
  const row = "ABCD"[Math.floor(index / 8)];
  return `${row}${(index % 8) + 1}`;
});

// Keep API calls in one place so error handling stays consistent.
async function api(path, options = {}) {
  const response = await fetch(path, {
    credentials: "same-origin",
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {})
    },
    ...options
  });

  const data = response.status === 204
    ? null
    : await response.json().catch(() => ({}));

  if (!response.ok) {
    throw new Error(data?.error || `Request failed (${response.status})`);
  }

  return data;
}

function showMessage(id, message, success = false) {
  const element = $(id);
  element.textContent = message;
  element.className = `message ${success ? "success" : "error"}`;
}

function posterSlug(title) {
  return String(title)
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-|-$/g, "");
}

async function loadUser() {
  try {
    currentUser = await api("/api/auth/me");
  } catch {
    currentUser = null;
  }

  renderAccount();
}

function renderAccount() {
  const accountBar = $("accountBar");
  accountBar.innerHTML = "";

  if (!currentUser) {
    const signInButton = document.createElement("button");
    signInButton.className = "button secondary";
    signInButton.textContent = "Sign in / Register";
    signInButton.onclick = () => $("auth").scrollIntoView({ behavior: "smooth" });
    accountBar.appendChild(signInButton);

    $("adminNav").hidden = true;
    $("admin").hidden = true;
    return;
  }

  const greeting = document.createElement("span");
  greeting.textContent = `Hi, ${currentUser.name}`;
  accountBar.appendChild(greeting);

  const signOutButton = document.createElement("button");
  signOutButton.className = "button secondary";
  signOutButton.textContent = "Sign out";
  signOutButton.onclick = async () => {
    try {
      await api("/api/auth/logout", { method: "POST" });
      currentUser = null;
      renderAccount();
      await loadBookings();
      showMessage("bookingMessage", "You signed out.", true);
    } catch (error) {
      alert(error.message);
    }
  };
  accountBar.appendChild(signOutButton);

  const isAdmin = currentUser.role === "ADMIN";
  $("adminNav").hidden = !isAdmin;
  $("admin").hidden = !isAdmin;
}

async function loadMovies() {
  try {
    const searchText = encodeURIComponent($("search").value);
    movies = await api(`/api/movies?search=${searchText}`);

    const movieGrid = $("movieGrid");
    movieGrid.innerHTML = "";

    if (movies.length === 0) {
      movieGrid.textContent = "No movies found. Try a different title.";
    }

    movies.forEach((movie) => {
      const card = document.createElement("article");
      card.className = "movie-card";

      const poster = document.createElement("img");
      poster.className = "movie-poster";
      poster.src = `/posters/${posterSlug(movie.title)}.svg`;
      poster.alt = `${movie.title} poster artwork`;
      poster.loading = "lazy";
      poster.onerror = () => {
        poster.src = "/posters/cinebook-default.svg";
      };

      const details = document.createElement("div");
      details.className = "movie-details";

      const title = document.createElement("h3");
      title.textContent = movie.title;

      const genre = document.createElement("p");
      genre.textContent = movie.genre;

      const duration = document.createElement("p");
      duration.textContent = `${movie.duration} minutes`;

      const price = document.createElement("div");
      price.className = "price";
      price.textContent = `₹${Number(movie.price).toFixed(0)} per ticket`;

      details.append(title, genre, duration, price);
      card.append(poster, details);
      movieGrid.appendChild(card);
    });

    // Keep the booking dropdown in sync with the search results.
    const movieSelect = $("movieSelect");
    const previousMovie = movieSelect.value;
    movieSelect.innerHTML = "";

    movies.forEach((movie) => {
      const option = document.createElement("option");
      option.value = movie.id;
      option.textContent = movie.title;
      movieSelect.appendChild(option);
    });

    if ([...movieSelect.options].some((option) => option.value === previousMovie)) {
      movieSelect.value = previousMovie;
    }

    await loadSeats();

    if (currentUser?.role === "ADMIN") {
      renderAdminMovies();
    }
  } catch (error) {
    $("movieGrid").textContent = `Could not load movies: ${error.message}`;
  }
}

async function loadSeats() {
  selectedSeat = null;
  const movieId = $("movieSelect").value;

  if (!movieId) {
    $("seats").innerHTML = "";
    return;
  }

  try {
    const theatre = $("theatreSelect").value;
    const showTime = $("showTime").value;
    const query = `movieId=${movieId}&showTime=${encodeURIComponent(showTime)}`
      + `&theatre=${encodeURIComponent(theatre)}`;
    const result = await api(`/api/seats?${query}`);

    const seatArea = $("seats");
    seatArea.innerHTML = "";

    seatNames.forEach((seatName) => {
      const seatButton = document.createElement("button");
      seatButton.type = "button";
      seatButton.textContent = seatName;

      const isBooked = (result.bookedSeats || []).includes(seatName);
      seatButton.className = `seat ${isBooked ? "booked" : ""}`;
      seatButton.disabled = isBooked;
      seatButton.title = isBooked ? "Already booked" : "Available seat";

      seatButton.onclick = () => {
        selectedSeat = seatName;
        document.querySelectorAll(".seat.selected").forEach((seat) => {
          seat.classList.remove("selected");
        });
        seatButton.classList.add("selected");
      };

      seatArea.appendChild(seatButton);
    });
  } catch (error) {
    showMessage("bookingMessage", error.message);
  }
}

async function loadBookings() {
  const tableBody = $("bookingRows");
  tableBody.innerHTML = "";

  if (!currentUser) {
    tableBody.innerHTML = '<tr><td colspan="8">Sign in to view bookings.</td></tr>';
    return;
  }

  try {
    const bookings = await api("/api/bookings");

    if (bookings.length === 0) {
      tableBody.innerHTML = '<tr><td colspan="8">No bookings yet.</td></tr>';
      return;
    }

    bookings.forEach((booking) => {
      const row = document.createElement("tr");
      const values = [
        booking.id,
        booking.customerName,
        booking.movieTitle,
        booking.theatre,
        booking.showTime,
        booking.seat,
        booking.status
      ];

      values.forEach((value) => {
        const cell = document.createElement("td");
        cell.textContent = String(value ?? "");
        row.appendChild(cell);
      });

      const actionCell = document.createElement("td");
      if (booking.status === "BOOKED") {
        const cancelButton = document.createElement("button");
        cancelButton.className = "button secondary";
        cancelButton.textContent = "Cancel";
        cancelButton.onclick = async () => {
          if (!confirm(`Cancel booking #${booking.id}?`)) return;

          try {
            await api(`/api/bookings/${booking.id}`, { method: "DELETE" });
            await loadBookings();
            await loadSeats();
          } catch (error) {
            alert(error.message);
          }
        };
        actionCell.appendChild(cancelButton);
      } else {
        actionCell.textContent = "—";
      }

      row.appendChild(actionCell);
      tableBody.appendChild(row);
    });
  } catch (error) {
    tableBody.innerHTML = `<tr><td colspan="8">${error.message}</td></tr>`;
  }
}

async function renderAdminMovies() {
  if (currentUser?.role !== "ADMIN") return;

  const list = $("adminMovies");
  list.innerHTML = "";

  movies.forEach((movie) => {
    const row = document.createElement("div");
    row.className = "admin-row";

    const details = document.createElement("span");
    details.textContent = `${movie.title} · ${movie.genre} · ₹${movie.price}`;

    const deleteButton = document.createElement("button");
    deleteButton.className = "button secondary";
    deleteButton.textContent = "Delete";
    deleteButton.onclick = async () => {
      if (!confirm(`Delete ${movie.title}? Movies with booking history cannot be deleted.`)) {
        return;
      }

      try {
        await api(`/api/movies/${movie.id}`, { method: "DELETE" });
        await loadMovies();
      } catch (error) {
        alert(error.message);
      }
    };

    row.append(details, deleteButton);
    list.appendChild(row);
  });
}

$("openAuth").onclick = () => $("auth").scrollIntoView({ behavior: "smooth" });
$("search").addEventListener("input", loadMovies);
$("movieSelect").addEventListener("change", loadSeats);
$("theatreSelect").addEventListener("change", loadSeats);
$("showTime").addEventListener("change", loadSeats);
$("refreshBookings").onclick = loadBookings;

$("loginForm").addEventListener("submit", async (event) => {
  event.preventDefault();

  try {
    currentUser = await api("/api/auth/login", {
      method: "POST",
      body: JSON.stringify({
        email: $("loginEmail").value,
        password: $("loginPassword").value
      })
    });

    showMessage("loginMessage", "Signed in successfully.", true);
    $("loginForm").reset();
    renderAccount();
    await loadBookings();
    await loadMovies();
  } catch (error) {
    showMessage("loginMessage", error.message);
  }
});

$("registerForm").addEventListener("submit", async (event) => {
  event.preventDefault();

  try {
    await api("/api/auth/register", {
      method: "POST",
      body: JSON.stringify({
        name: $("registerName").value,
        email: $("registerEmail").value,
        password: $("registerPassword").value
      })
    });

    showMessage("registerMessage", "Account created. Sign in using your email and password.", true);
    $("registerForm").reset();
  } catch (error) {
    showMessage("registerMessage", error.message);
  }
});

$("bookingForm").addEventListener("submit", async (event) => {
  event.preventDefault();

  if (!currentUser) {
    showMessage("bookingMessage", "Please sign in before booking.");
    $("auth").scrollIntoView({ behavior: "smooth" });
    return;
  }

  if (!selectedSeat) {
    showMessage("bookingMessage", "Please select an available seat.");
    return;
  }

  try {
    await api("/api/bookings", {
      method: "POST",
      body: JSON.stringify({
        movieId: Number($("movieSelect").value),
        showTime: $("showTime").value,
        theatre: $("theatreSelect").value,
        seat: selectedSeat
      })
    });

    showMessage("bookingMessage", `Success! Seat ${selectedSeat} is booked.`, true);
    selectedSeat = null;
    await loadSeats();
    await loadBookings();
  } catch (error) {
    showMessage("bookingMessage", error.message);
    await loadSeats();
  }
});

$("movieForm").addEventListener("submit", async (event) => {
  event.preventDefault();

  try {
    await api("/api/movies", {
      method: "POST",
      body: JSON.stringify({
        title: $("newTitle").value,
        genre: $("newGenre").value,
        duration: Number($("newDuration").value),
        price: Number($("newPrice").value)
      })
    });

    showMessage("adminMessage", "Movie added.", true);
    $("movieForm").reset();
    await loadMovies();
  } catch (error) {
    showMessage("adminMessage", error.message);
  }
});

// Load the signed-in account first so booking history uses the right session.
(async function startApp() {
  await loadUser();
  await loadMovies();
  await loadBookings();
})();
