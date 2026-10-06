package org.example.relationaldatabase.relational.child;

import jakarta.persistence.*;
import lombok.Data;
import org.example.relationaldatabase.relational.parent.Parent;

import java.util.Optional;

@Entity
@Data
public class Child {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional=false)
    @JoinColumn(name = "parent_id")
    private Parent parent;
}
