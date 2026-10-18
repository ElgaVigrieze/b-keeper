package lv.bkeeper.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;


@RequiredArgsConstructor
public abstract class BaseCrudController<T extends Identifiable ,R extends CrudRepository<T, Long>> {

    protected abstract R getRepo();
    protected abstract String getEntityName();
    protected abstract T createDefaultEntityForm();
    protected abstract void saveExisting(T submitted);
    protected abstract void saveNew(T submitted);

    @GetMapping
    public String list(Model model) {
        model.addAttribute(getEntityName() + "s", getRepo().findAll());
        model.addAttribute("currentPage", getEntityName() + "s");
        return getEntityName() + "s/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute(getEntityName(), createDefaultEntityForm());
        model.addAttribute("currentPage", getEntityName() + "s");
        return getEntityName() + "s/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        getRepo().findById(id).ifPresent(e -> model.addAttribute(getEntityName(), e));
        model.addAttribute("currentPage", getEntityName() + "s");
        return getEntityName() + "s/form";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        getRepo().deleteById(id);
        ra.addFlashAttribute("success", getSuccessMessage(getEntityName(), "delete"));
        return "redirect:/" + getEntityName()+ "s";
    }


    @PostMapping("/save")
    public String save(@ModelAttribute T submitted, RedirectAttributes ra) {
        System.out.println(submitted);
        if (submitted.getId() != null) {
            saveExisting(submitted);
        } else {
            saveNew(submitted);
        }
        ra.addFlashAttribute("success", getSuccessMessage(getEntityName(), "save"));
        return "redirect:/" + getEntityName()+ "s";
    }

    private Object getSuccessMessage(String entityName, String action) {
        String entityInLv = null;
        String actionInLv = null;
        switch (entityName) {
            case "customer" -> entityInLv = "Klients";
            case "employee" -> entityInLv = "Darbinieks";
            case "supplier" -> entityInLv = "Piegādātājs";
            case "asset" -> entityInLv = "Aktīvs";
        }
        switch (action) {
            case "delete" -> actionInLv = " dzēsts";
            case "save" -> actionInLv = " saglabāts";
        }
        return entityInLv + actionInLv;
    }


}
