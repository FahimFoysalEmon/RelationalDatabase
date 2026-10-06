package org.example.relationaldatabase.relational.parent;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Validated
public class ParentController {

    private final ParentService parentService;

    @PostMapping(value = "/api/save/parent")
    public Object saveParent(@RequestBody Parent parent){
        return parentService.saveParent(parent);
    }


    @GetMapping(value = "/api/parents")
    public List<Parent> findAllParents(@RequestParam (required = false) Long parentId){
        return parentService.findAllParents(parentId);
    }

}
