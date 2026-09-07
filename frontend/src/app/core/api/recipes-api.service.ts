import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';

export interface RecipeIngredient {
    ingredientId: string;
    quantity: number;
    unit: string;
}

export interface RecipeVersion {
    id: string;
    dishId: string;
    versionNumber: number;
    ingredients: RecipeIngredient[];
    instructions: string[];
    changeReason: string;
    createdAt: string;
    active: boolean;
}

export interface MenuCategory {
    id: string;
    name: string;
}

export interface CreateDishRequest {
    name: string;
    price: number;
    categoryId: string;
    preparationMinutes: number;
    ingredients: RecipeIngredient[];
    instructions: string[];
}

@Injectable({ providedIn: 'root' })
export class RecipesApiService {
    private readonly http = inject(HttpClient);

    list() {
        return this.http.get<RecipeVersion[]>('/api/recipes');
    }

    categories() {
        return this.http.get<MenuCategory[]>('/api/dish-categories');
    }

    createDish(request: CreateDishRequest) {
        return this.http.post<RecipeVersion>('/api/recipes/dishes', request);
    }

    propose(dishId: string, recipe: RecipeVersion, changeReason: string) {
        return this.http.post<RecipeVersion>(
            `/api/recipes/dishes/${dishId}/proposals`,
            {
                ingredients: recipe.ingredients,
                instructions: recipe.instructions,
                changeReason
            }
        );
    }

    activate(recipeId: string) {
        return this.http.post<RecipeVersion>(
            `/api/recipes/${recipeId}/activate`,
            {}
        );
    }
}