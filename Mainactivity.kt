Mainactivity.kt
package com.example.gramaangana

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.google.firebase.firestore.FirebaseFirestore
import io.github.boguszpawlowski.composecalendar.SelectableCalendar
import io.github.boguszpawlowski.composecalendar.rememberSelectableCalendarState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                GramaAnganaApp()
            }
        }
    }
}

data class Booking(
    val id: String = "",
    val date: String = "",
    val slot: String = "",
    val eventName: String = "",
    val userName: String = "",
    val status: String = "PENDING"
)

@Entity(tableName = "maintenance")
data class MaintenanceItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val itemName: String,
    val targetAmount: Int,
    val currentAmount: Int = 0
)

@Dao
interface MaintenanceDao {
    @Query("SELECT * FROM maintenance")
    fun getAllItems(): Flow<List<MaintenanceItem>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: MaintenanceItem)
    @Update
    suspend fun update(item: MaintenanceItem)
}

@Database(entities = [MaintenanceItem::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun maintenanceDao(): MaintenanceDao
    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "gramaangana_db").build().also { INSTANCE = it }
            }
        }
    }
}

@Composable
fun GramaAnganaApp() {
    val navController = rememberNavController()
    val items = listOf(NavItem.Calendar, NavItem.Maintenance, NavItem.Events, NavItem.Admin)
    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                items.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title) },
                        selected = currentRoute == item.route,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(navController, NavItem.Calendar.route, Modifier.padding(padding)) {
            composable(NavItem.Calendar.route) { CalendarScreen() }
            composable(NavItem.Maintenance.route) { MaintenanceScreen() }
            composable(NavItem.Events.route) { EventsScreen() }
            composable(NavItem.Admin.route) { AdminScreen() }
        }
    }
}

sealed class NavItem(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Calendar : NavItem("calendar", "Calendar", Icons.Default.DateRange)
    object Maintenance : NavItem("maintenance", "Jar", Icons.Default.Build)
    object Events : NavItem("events", "Events", Icons.AutoMirrored.Filled.List)
    object Admin : NavItem("admin", "Admin", Icons.Default.Lock)
}

@Composable
fun CalendarScreen() {
    val db = FirebaseFirestore.getInstance()
    var bookings by remember { mutableStateOf<List<Booking>>(emptyList()) }
    var showDialog by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

    LaunchedEffect(Unit) {
        db.collection("bookings").addSnapshotListener { snapshot, _ ->
            bookings = snapshot?.toObjects(Booking::class.java) ?: emptyList()
        }
    }

    val calendarState = rememberSelectableCalendarState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Grama Angana - Booking Calendar", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        SelectableCalendar(
            calendarState = calendarState,
            dayContent = { dayState ->
                val date = dayState.date
                val isBooked = bookings.any { it.date == date.toString() && it.status == "APPROVED" }
                Box(
                    modifier = Modifier.size(40.dp).padding(4.dp)
                        .background(if (isBooked) Color.Red.copy(0.3f) else Color.Green.copy(0.3f))
                        .clickable { selectedDate = date; showDialog = true },
                    contentAlignment = Alignment.Center
                ) { Text(date.dayOfMonth.toString()) }
            }
        )
        Spacer(Modifier.height(16.dp))
        Row {
            Box(Modifier.size(16.dp).background(Color.Green.copy(0.3f)))
            Text(" Free  ")
            Box(Modifier.size(16.dp).background(Color.Red.copy(0.3f)))
            Text(" Booked")
        }
    }

    if (showDialog && selectedDate != null) {
        RequestBookingDialog(
            date = selectedDate!!,
            onDismiss = { showDialog = false },
            onSubmit = { eventName, slot, userName ->
                val booking = Booking(
                    id = db.collection("bookings").document().id,
                    date = selectedDate.toString(),
                    slot = slot,
                    eventName = eventName,
                    userName = userName,
                    status = "PENDING"
                )
                db.collection("bookings").document(booking.id).set(booking)
                showDialog = false
            }
        )
    }
}

@Composable
fun RequestBookingDialog(date: LocalDate, onDismiss: () -> Unit, onSubmit: (String, String, String) -> Unit) {
    var eventName by remember { mutableStateOf("") }
    var slot by remember { mutableStateOf("Morning") }
    var userName by remember { mutableStateOf("") }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Request Booking for ${date.format(DateTimeFormatter.ISO_DATE)}") },
        text = {
            Column {
                OutlinedTextField(value = eventName, onValueChange = { eventName = it }, label = { Text("Event Name") })
                OutlinedTextField(value = userName, onValueChange = { userName = it }, label = { Text("Your Name") })
                Spacer(Modifier.height(8.dp))
                Text("Slot:")
                Row {
                    listOf("Morning", "Afternoon", "Evening").forEach {
                        Row(Modifier.clickable { slot = it }) {
                            RadioButton(selected = slot == it, onClick = { slot = it })
                            Text(it)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val db = FirebaseFirestore.getInstance()
                db.collection("bookings").whereEqualTo("date", date.toString()).whereEqualTo("slot", slot).whereEqualTo("status", "APPROVED").get()
                    .addOnSuccessListener { docs ->
                        if (docs.isEmpty) { onSubmit(eventName, slot, userName) }
                        else { Toast.makeText(context, "This slot is already booked!", Toast.LENGTH_SHORT).show() }
                    }
            }) { Text("Submit") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun MaintenanceScreen() {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val dao = db.maintenanceDao()
    val items by dao.getAllItems().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (items.isEmpty()) {
            dao.insert(MaintenanceItem(itemName = "Ceiling Fan", targetAmount = 500, currentAmount = 120))
            dao.insert(MaintenanceItem(itemName = "LED Bulbs", targetAmount = 200, currentAmount = 50))
            dao.insert(MaintenanceItem(itemName = "Plastic Chairs", targetAmount = 1000, currentAmount = 300))
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Maintenance Jar", style = MaterialTheme.typography.headlineMedium)
            Text("Crowdfund for repairs", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
        }
        items(items) { item ->
            Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(item.itemName, fontWeight = FontWeight.Bold)
                    Text("₹${item.currentAmount} / ₹${item.targetAmount}")
                    LinearProgressIndicator(progress = { item.currentAmount.toFloat() / item.targetAmount }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    Button(onClick = { scope.launch { dao.update(item.copy(currentAmount = item.currentAmount + 50)) } }) {
                        Text("Pledge ₹50")
                    }
                }
            }
        }
    }
}

@Composable
fun EventsScreen() {
    val db = FirebaseFirestore.getInstance()
    var approvedBookings by remember { mutableStateOf<List<Booking>>(emptyList()) }
    val today = LocalDate.now().toString()

    LaunchedEffect(Unit) {
        db.collection("bookings").whereEqualTo("status", "APPROVED").addSnapshotListener { snapshot, _ ->
            approvedBookings = snapshot?.toObjects(Booking::class.java) ?: emptyList()
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Event Board - Today", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        val todaysEvents = approvedBookings.filter { it.date == today }
        if (todaysEvents.isEmpty()) { Text("No events today. Hall is free!") }
        else {
            LazyColumn {
                items(todaysEvents) { booking ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(booking.eventName, fontWeight = FontWeight.Bold)
                            Text("${booking.slot} - By ${booking.userName}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminScreen() {
    val db = FirebaseFirestore.getInstance()
    var pendingBookings by remember { mutableStateOf<List<Booking>>(emptyList()) }
    var isAdmin by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        db.collection("bookings").whereEqualTo("status", "PENDING").addSnapshotListener { snapshot, _ ->
            pendingBookings = snapshot?.toObjects(Booking::class.java) ?: emptyList()
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Panchayat Admin Panel", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        if (!isAdmin) {
            OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Admin Password") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Button(onClick = { if (password == "grama123") isAdmin = true }, modifier = Modifier.fillMaxWidth()) { Text("Login as Admin") }
            Text("Hint: password is grama123", style = MaterialTheme.typography.bodySmall)
        } else {
            Text("Pending Requests: ${pendingBookings.size}", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            if (pendingBookings.isEmpty()) { Text("No pending bookings") }
            else {
                LazyColumn {
                    items(pendingBookings) { booking ->
                        AdminBookingCard(
                            booking = booking,
                            onApprove = { db.collection("bookings").document(booking.id).update("status", "APPROVED") },
                            onReject = { db.collection("bookings").document(booking.id).update("status", "REJECTED") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AdminBookingCard(booking: Booking, onApprove: () -> Unit, onReject: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(booking.eventName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text("Date: ${booking.date}")
            Text("Slot: ${booking.slot}")
            Text("Requested by: ${booking.userName}")
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(onClick = onApprove, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) {
                    Icon(Icons.Default.Check, contentDescription = "Approve")
                    Spacer(Modifier.width(4.dp))
                    Text("Approve")
                }
                Button(onClick = onReject, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))) {
                    Icon(Icons.Default.Close, contentDescription = "Reject")
                    Spacer(Modifier.width(4.dp))
                    Text("Reject")
                }
            }
        }
    }
}

build.gradle.kts(gramaangana)
plugins {
    id("com.android.application") version "8.5.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false  // ← THIS LINE IS MISSING
}

build.gradle.kts(:app)
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.gramaangana"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.gramaangana"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

kotlin {
    jvmToolchain(8)
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-firestore-ktx")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("io.github.boguszpawlowski.composecalendar:composecalendar:1.1.0")

    // Added to fix test errors
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
