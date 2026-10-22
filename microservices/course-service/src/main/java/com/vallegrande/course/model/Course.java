package com.vallegrande.course.model;

public class Course {
    private String id;
    private String title;
    private String instructor;
    private String category;
    private int availableSeats;
    private int totalSeats;
    private double price;
    private boolean active;

    public Course() {
    }

    public Course(String id, String title, String instructor, String category, int availableSeats, int totalSeats, double price, boolean active) {
        this.id = id;
        this.title = title;
        this.instructor = instructor;
        this.category = category;
        this.availableSeats = availableSeats;
        this.totalSeats = totalSeats;
        this.price = price;
        this.active = active;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getInstructor() {
        return instructor;
    }

    public void setInstructor(String instructor) {
        this.instructor = instructor;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public int getAvailableSeats() {
        return availableSeats;
    }

    public void setAvailableSeats(int availableSeats) {
        this.availableSeats = availableSeats;
    }

    public int getTotalSeats() {
        return totalSeats;
    }

    public void setTotalSeats(int totalSeats) {
        this.totalSeats = totalSeats;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
