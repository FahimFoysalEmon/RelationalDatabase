package org.example.relationaldatabase.relational.parent;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ParentService {

    private final ParentRepository parentRepository;

    public Object saveParent(Parent parent) {
        return parentRepository.save(parent);
    }

    public List<Parent> findAllParents(Long parentId) {

        if (parentId == null) {
            return parentRepository.findAll();
        } else {
            return parentRepository.findById(parentId).stream().toList();
        }

    }
}
