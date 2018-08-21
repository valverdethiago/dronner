package io.resin.droner.service.impl;

import io.resin.droner.entities.Coordinate;
import io.resin.droner.entities.Drone;
import io.resin.droner.entities.Status;
import io.resin.droner.repository.repository.DroneRepository;
import io.resin.droner.service.CoordinateService;
import io.resin.droner.service.DroneService;
import io.resin.droner.service.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DroneServiceImpl implements DroneService {

    @Autowired
    private DroneRepository repository;
    @Autowired
    private CoordinateService coordinateService;

    @Override
    public Drone registerDrone() {
        return repository.saveOrUpdate(Drone.builder().build());
    }

    @Override
    public void updateCoordinates(UUID id, Coordinate coordinate) throws EntityNotFoundException{
        Optional<Drone> drone = this.repository.fetch(id);
        if (!drone.isPresent()) {
            throw new EntityNotFoundException(String.format("Drone with id %s not found", id));
        }
        // Cloning coordinate to avoid concurrency problems
        Coordinate coordinateToSave = Coordinate.builder()
                .latitude(coordinate.getLatitude())
                .longitude(coordinate.getLongitude()).build();
        coordinateToSave.setTime(Instant.now());
        drone.get().getCoordinates().add(coordinateToSave);
        this.repository.saveOrUpdate(drone.get());
    }

    @Override
    public Collection<Drone> listAll() {
        Set<Drone> drones = this.repository.all();
        drones.forEach(drone ->  fillStatus(drone));
        return drones;
    }

    @Override
    public boolean isAlive(UUID id) throws EntityNotFoundException{
        if(id == null) {
            throw new IllegalArgumentException("Drone Id must be informed to get status");
        }
        Optional<Drone> drone = this.repository.fetch(id);
        if (!drone.isPresent()) {
            throw new EntityNotFoundException(String.format("Drone with id %s not found", id));
        }
        return isAlive(drone.get());
    }

    @Override
    public boolean isStuck(UUID id) throws EntityNotFoundException{
        if(id == null) {
            throw new IllegalArgumentException("Drone Id must be informed to get status");
        }
        Optional<Drone> drone = this.repository.fetch(id);
        if (!drone.isPresent()) {
            throw new EntityNotFoundException(String.format("Drone with id %s not found", id));
        }
        return isStuck(drone.get());
    }

    @Override
    public boolean isAlive(Drone drone) {
        Instant now = Instant.now();
        //filter coordinates sent in the last 10 seconds
        List<Coordinate> recentSentCoordinates = drone.getCoordinates()
                .stream()
                .filter(coordinate -> {
                    Long diffInSeconds = now.getEpochSecond() - coordinate.getTime().getEpochSecond();
                    return diffInSeconds <= 10;
                })
                .collect(Collectors.toList());
        return !recentSentCoordinates.isEmpty();
    }

    @Override
    public boolean isStuck(Drone drone) {
        Instant now = Instant.now();
        //filter coordinates sent in the last 10 seconds
        List<Coordinate> recentSentCoordinates = drone.getCoordinates()
                .stream()
                .filter(coordinate -> {
                    Long diffInSeconds = now.getEpochSecond() - coordinate.getTime().getEpochSecond();
                    return diffInSeconds <= 10;
                })
                .collect(Collectors.toList());
        if (recentSentCoordinates.size() == 0) {
            return true;
        }
        //if drone has one single coordinate sent and it's recent it is not stuck
        else if(recentSentCoordinates.size() == 1) {
            return false;
        }
        else {
            Collections.sort(recentSentCoordinates);
            Coordinate first = recentSentCoordinates.get(0);
            Coordinate last = recentSentCoordinates.get(recentSentCoordinates.size() - 1);
            Double distance = this.coordinateService.calculateDistance(first, last);
            return distance <= 1;
        }
    }

    @Override
    public Drone fetch(UUID id) throws EntityNotFoundException {
        if(id == null) {
            throw new IllegalArgumentException("Drone Id must be informed to fetch");
        }
        Optional<Drone> drone = this.repository.fetch(id);
        if (!drone.isPresent()) {
            throw new EntityNotFoundException(String.format("Drone with id %s not found", id));
        }
        this.fillStatus(drone.get());
        return drone.get();
    }

    private void fillStatus(Drone drone) {
        boolean isAlive = this.isAlive(drone);
        if(!isAlive) {
            drone.setStatus(Status.DEAD);
        }
        else {
            drone.setStatus(this.isStuck(drone) ? Status.STUCK : Status.MOVING);
        }
    }
}
