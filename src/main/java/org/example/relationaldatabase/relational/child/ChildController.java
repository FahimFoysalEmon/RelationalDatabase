package org.example.relationaldatabase.relational.child;

import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Validated
public class ChildController {

    private final ChildService childService;

    @PostMapping(value = "/api/save/child")
    public Child saveChild(@RequestBody Child child){
        return childService.saveChild(child);
    }

    @GetMapping(value = "/api/childs")
    public List<Child> getChilds(@RequestParam (required = false) Long childId){
        return childService.getChilds(childId);
    }

}
