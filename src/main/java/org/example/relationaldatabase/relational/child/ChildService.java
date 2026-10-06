package org.example.relationaldatabase.relational.child;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChildService {

    private final ChildRepository childRepository;

    public Child saveChild(Child child) {
        return childRepository.save(child);
    }

    public List<Child> getChilds(Long childId) {
        if (childId == null) {
            return childRepository.findAll();
        } else {
            return childRepository.findById(childId).stream().toList();
        }
    }
}
