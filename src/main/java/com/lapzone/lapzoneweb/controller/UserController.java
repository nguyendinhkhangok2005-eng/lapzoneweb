package com.lapzone.lapzoneweb.controller;

import com.lapzone.lapzoneweb.model.dto.UserUpdateDTO;
import com.lapzone.lapzoneweb.model.entity.User;
import com.lapzone.lapzoneweb.model.service.UserService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class UserController {

    @Autowired
    private UserService userService;
    @GetMapping("/profile")
    public String showProfile(HttpSession session, Model model) {
        User sessionUser = (User) session.getAttribute("currentUser");

        if (sessionUser == null) {
            return "redirect:/dangnhap";
        }

        User currentUser = userService.findById(sessionUser.getId());
        UserUpdateDTO dto = new UserUpdateDTO(
            currentUser.getId(),
            currentUser.getFullName(),
            currentUser.getPhone(),
            currentUser.getAddress()
        );
        model.addAttribute("userForm", dto);
        model.addAttribute("user", currentUser); 
        return "profile";
    }
    @PostMapping("/profile/update")
    public String handleUpdateProfile(@Valid @ModelAttribute("userForm") UserUpdateDTO dto,
                                      BindingResult result,
                                      HttpSession session,
                                      Model model) {

        User sessionUser = (User) session.getAttribute("currentUser");
        if (sessionUser == null) {
            return "redirect:/dangnhap";
        }
        dto.setId(sessionUser.getId());

        if (result.hasErrors()) {
            User currentUser = userService.findById(sessionUser.getId());
            model.addAttribute("user", currentUser);
            return "profile";
        }

        User updatedUser = userService.updateProfileFromDTO(dto);

        if (updatedUser != null) {
            session.setAttribute("currentUser", updatedUser);
            model.addAttribute("success", "Cập nhật thông tin thành công!");
            model.addAttribute("user", updatedUser);
            model.addAttribute("userForm", new UserUpdateDTO(
                updatedUser.getId(), updatedUser.getFullName(),
                updatedUser.getPhone(), updatedUser.getAddress()
            ));
        } else {
            model.addAttribute("error", "Có lỗi xảy ra khi cập nhật!");
            model.addAttribute("user", userService.findById(sessionUser.getId()));
            model.addAttribute("userForm", dto);
        }

        return "profile";
    }
}
