package com.vallegrande.enrollment.model;

public class CourseDto {
    private String id;
    private String title;
    private String instructor;
    private int availableSeats;
    private double price;
    private boolean active;

    public CourseDto() {
    }

    public CourseDto(String id, String title, String instructor, int availableSeats, double price, boolean active) {
        this.id = id;
        this.title = title;
        this.instructor = instructor;
        this.availableSeats = availableSeats;
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

    public int getAvailableSeats() {
        return availableSeats;
    }

    public void setAvailableSeats(int availableSeats) {
        this.availableSeats = availableSeats;
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
