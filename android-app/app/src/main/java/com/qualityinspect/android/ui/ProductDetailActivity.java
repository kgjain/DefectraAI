package com.qualityinspect.android.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.qualityinspect.android.R;
import com.qualityinspect.android.api.ApiClient;
import com.qualityinspect.android.api.Models.Product;
import com.qualityinspect.android.api.Models.ProductUpdateRequest;
import com.qualityinspect.android.data.SessionManager;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProductDetailActivity extends AppCompatActivity {

    private long id;
    private Product current;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_detail);
        setTitle("Product");
        id = getIntent().getLongExtra("id", -1);
        session = ((com.qualityinspect.android.QualityInspectApp) getApplication()).getSession();
        TextView tv = findViewById(R.id.tv_detail);
        Button btnAi = findViewById(R.id.btn_product_ai);
        btnAi.setOnClickListener(v -> {
            Intent i = new Intent(ProductDetailActivity.this, AiAnalysisActivity.class);
            i.putExtra("productId", id);
            startActivity(i);
        });

        if (session.isQualityManager()) {
            LinearLayout ll = findViewById(R.id.ll_product_buttons);
            Button edit = new Button(this);
            edit.setText("Edit product…");
            edit.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            edit.setOnClickListener(v -> editProduct());
            ll.addView(edit);

            Button archive = new Button(this);
            archive.setText("Archive product");
            archive.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            archive.setOnClickListener(v -> confirmArchive());
            ll.addView(archive);
        }

        ApiClient.get().product(id).enqueue(new Callback<Product>() {
            @Override
            public void onResponse(Call<Product> c, Response<Product> r) {
                if (r.isSuccessful() && r.body() != null) {
                    current = r.body();
                    tv.setText("Code: " + current.code + "\nName: " + current.name
                            + "\nCategory: " + current.category + "\nStatus: " + current.status
                            + "\nDescription: " + current.description
                            + "\nSpecs: " + current.specifications);
                }
            }
            @Override
            public void onFailure(Call<Product> c, Throwable t) {
                tv.setText("Error: " + t.getMessage());
            }
        });
    }

    private void editProduct() {
        if (current == null) return;
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(48, 24, 48, 0);
        android.widget.EditText code = field("Code", current.code);
        android.widget.EditText name = field("Name", current.name);
        android.widget.EditText category = field("Category", current.category);
        android.widget.EditText description = field("Description", current.description);
        wrap.addView(code); wrap.addView(name); wrap.addView(category); wrap.addView(description);
        new AlertDialog.Builder(this)
                .setTitle("Edit product")
                .setView(wrap)
                .setPositiveButton("Save", (d, w) -> {
                    ProductUpdateRequest req = new ProductUpdateRequest();
                    req.code = text(code);
                    req.name = text(name);
                    req.category = text(category);
                    req.description = text(description);
                    ApiClient.get().updateProduct(id, req).enqueue(new Callback<Product>() {
                        @Override public void onResponse(Call<Product> c, Response<Product> r) {
                            Toast.makeText(ProductDetailActivity.this,
                                    r.isSuccessful() ? "Product updated" : DefectDetailActivity.err(r),
                                    Toast.LENGTH_LONG).show();
                            recreate();
                        }
                        @Override public void onFailure(Call<Product> c, Throwable t) {
                            Toast.makeText(ProductDetailActivity.this, t.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmArchive() {
        new AlertDialog.Builder(this)
                .setTitle("Archive product?")
                .setMessage("Archived products keep their inspection history but cannot start new batches.")
                .setPositiveButton("Archive", (d, w) ->
                        ApiClient.get().archiveProduct(id).enqueue(new Callback<Product>() {
                            @Override public void onResponse(Call<Product> c, Response<Product> r) {
                                Toast.makeText(ProductDetailActivity.this,
                                        r.isSuccessful() ? "Product archived" : DefectDetailActivity.err(r),
                                        Toast.LENGTH_LONG).show();
                                recreate();
                            }
                            @Override public void onFailure(Call<Product> c, Throwable t) {
                                Toast.makeText(ProductDetailActivity.this, t.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private android.widget.EditText field(String hint, String value) {
        android.widget.EditText et = new android.widget.EditText(this);
        et.setHint(hint);
        if (value != null) et.setText(value);
        return et;
    }

    private static String text(android.widget.EditText et) {
        String s = et.getText().toString().trim();
        return s.isEmpty() ? null : s;
    }
}
