# Hotel Management System – GitHub Cleanup

## Recommended Repository Structure

```text
hotel-management-system/
│
├── src/
│   ├── Main.java
│   ├── Customer.java
│   ├── Room.java
│   ├── Booking.java
│   └── DatabaseConnection.java
│
├── screenshots/
│   ├── login-page.png
│   ├── dashboard.png
│   └── booking-page.png
│
├── README.md
├── .gitignore
└── pom.xml
```

---

# README.md

```md
# Hotel Management System

A desktop-based hotel management system developed using Java, JavaFX, JDBC, and MySQL.

## Features
- Login Authentication
- Room Booking
- Billing System
- Customer Management
- Database Integration

## Technologies Used
- Java
- JavaFX
- JDBC
- MySQL
- Maven

## Project Description
This project was developed as part of a semester project to automate hotel management operations such as room booking, customer management, and billing.

## How to Run
1. Clone the repository
2. Open the project in IntelliJ IDEA or Eclipse
3. Configure MySQL database connection
4. Run Main.java

## Screenshots
Add screenshots inside the `screenshots` folder.
```

---

# .gitignore

```gitignore
# Compiled class files
*.class

# Log files
*.log

# BlueJ files
*.ctxt

# Mobile Tools for Java (J2ME)
.mtj.tmp/

# Package Files #
*.jar
*.war
*.nar
*.ear
*.zip
*.tar.gz
*.rar

# Virtual machine crash logs
hs_err_pid*

# IntelliJ
.idea/
out/

# Eclipse
.project
.classpath
.settings/

# VS Code
.vscode/

# Database and temporary files
*.dat
*.txt
*.db
```

---

# Files You Should DELETE From GitHub

Delete these types of files from the repository:

```text
Main.class
Main$Customer.class
Main$Room.class
Receipt_*.txt
*.dat
output files
```

Keep ONLY:

```text
.java files
src folder
pom.xml
README.md
screenshots
```

---

# Steps To Update GitHub Repository

## Option 1 – Using GitHub Website

1. Open repository
2. Delete unnecessary `.class`, `.txt`, and `.dat` files
3. Upload `src` folder with `.java` files
4. Add README.md
5. Add screenshots folder

---

## Option 2 – Using Git Commands

```bash
git clone https://github.com/Ricky10-hub/hotel-management-system.git
cd hotel-management-system

# Remove unwanted files
rm *.class
rm *.dat
rm *.txt

# Add source code and README

git add .
git commit -m "Cleaned repository and added source code"
git push origin main
```
